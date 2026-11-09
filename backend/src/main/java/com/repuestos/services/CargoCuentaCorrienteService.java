package com.repuestos.services;

import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.CargoCuentaCorriente;
import com.repuestos.models.Cliente;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.CargoCuentaCorrienteRepository;
import com.repuestos.repositories.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CargoCuentaCorrienteService {

    private static final BigDecimal SALDO_MAXIMO = new BigDecimal("99999999.99");
    private static final int MOTIVO_MINIMO = 3;
    private static final int MOTIVO_MAXIMO = 300;
    private static final int RESUMEN_MOTIVO_MAXIMO = 80;

    private final CargoCuentaCorrienteRepository cargoRepository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public void validarDatos(BigDecimal monto, String motivo) {
        if (monto == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El monto es obligatorio");
        }
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El monto debe ser mayor a cero");
        }
        if (monto.stripTrailingZeros().scale() > 2) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El monto no puede tener más de 2 decimales");
        }
        String motivoNormalizado = motivo == null ? "" : motivo.trim();
        if (motivoNormalizado.length() < MOTIVO_MINIMO) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El motivo debe tener entre 3 y 300 caracteres");
        }
        if (motivoNormalizado.length() > MOTIVO_MAXIMO) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El motivo debe tener entre 3 y 300 caracteres");
        }
    }

    @Transactional
    public CargoCuentaCorriente registrar(Long clienteId, BigDecimal monto, String motivo,
                                          Usuario usuario, boolean clienteCreado) {
        validarDatos(monto, motivo);
        if (usuario == null) {
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        BigDecimal montoNormalizado = monto.setScale(2, RoundingMode.UNNECESSARY);
        String motivoNormalizado = motivo.trim();

        Cliente cliente = clienteRepository.findByIdParaActualizar(clienteId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Cliente no encontrado"));
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "El cliente '" + cliente.getNombre() + "' (#" + cliente.getId()
                            + ") está dado de baja. Reactivalo para usar su cuenta corriente.",
                    "CLIENTE_INACTIVO",
                    Map.of("clienteId", cliente.getId(), "clienteNombre", cliente.getNombre()));
        }

        if (cargoRepository.existsByCliente_IdAndUsuario_IdAndMontoAndMotivoAndFechaAfter(
                cliente.getId(), usuario.getId(), montoNormalizado, motivoNormalizado,
                LocalDateTime.now().minusSeconds(30))) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Ya cargaste esta misma deuda hace instantes",
                    "DEUDA_DUPLICADA");
        }

        BigDecimal saldoAntes = cliente.getSaldoCorriente() == null
                ? BigDecimal.ZERO.setScale(2)
                : cliente.getSaldoCorriente();
        BigDecimal saldoDespues = saldoAntes.add(montoNormalizado);
        if (saldoDespues.abs().compareTo(SALDO_MAXIMO) > 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El saldo resultante supera el máximo permitido de $99.999.999,99");
        }

        cliente.setSaldoCorriente(saldoDespues);
        clienteRepository.save(cliente);

        LocalDateTime ahora = LocalDateTime.now();
        CargoCuentaCorriente cargo = cargoRepository.save(CargoCuentaCorriente.builder()
                .cliente(cliente)
                .monto(montoNormalizado)
                .motivo(motivoNormalizado)
                .usuario(usuario)
                .fecha(ahora)
                .saldoAntes(saldoAntes)
                .saldoDespues(saldoDespues)
                .build());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("cargoId", cargo.getId());
        detalle.put("clienteId", cliente.getId());
        detalle.put("clienteNombre", cliente.getNombre());
        detalle.put("monto", montoNormalizado);
        detalle.put("motivo", motivoNormalizado);
        detalle.put("saldoAntes", saldoAntes);
        detalle.put("saldoDespues", saldoDespues);
        detalle.put("clienteCreado", clienteCreado);
        String resumen = "Cargó una deuda de $" + formatearMonto(montoNormalizado)
                + " a la cuenta corriente de '" + cliente.getNombre()
                + "' (motivo: " + recortarMotivo(motivoNormalizado) + ")";
        auditoriaService.registrarComo(AccionAuditoria.DEUDA_CARGADA, usuario,
                "Cliente", cliente.getId(), resumen, detalle);
        return cargo;
    }

    private String recortarMotivo(String motivo) {
        if (motivo.length() <= RESUMEN_MOTIVO_MAXIMO) {
            return motivo;
        }
        return motivo.substring(0, RESUMEN_MOTIVO_MAXIMO - 3) + "...";
    }

    private String formatearMonto(BigDecimal monto) {
        DecimalFormat formato = new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")));
        return formato.format(monto);
    }
}
