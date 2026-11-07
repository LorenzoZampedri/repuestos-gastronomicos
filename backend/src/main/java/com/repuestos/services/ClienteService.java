package com.repuestos.services;

import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Cliente;
import com.repuestos.models.Pago;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.PagoRepository;
import com.repuestos.repositories.PedidoRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.repositories.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClienteService {
    
    private final ClienteRepository clienteRepository;
    private final PagoRepository pagoRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final VentaService ventaService;
    private final AuditoriaService auditoriaService;
    private final FotoPerfilService fotoPerfilService;

    @Transactional
    public Cliente asegurarClienteParaUsuario(Usuario usuario) {
        if (usuario == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El usuario es obligatorio");
        }
        if (usuario.getCliente() != null) {
            return usuario.getCliente();
        }

        Cliente cliente = clienteRepository.save(Cliente.builder()
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .observaciones("Creado automáticamente desde el registro web")
                .saldoCorriente(BigDecimal.ZERO)
                .activo(true)
                .build());

        usuario.setCliente(cliente);
        usuarioRepository.save(usuario);
        fotoPerfilService.reconciliarVinculo(usuario, cliente);
        return cliente;
    }
    
    @Transactional
    public Usuario vincularClienteAUsuario(Long usuarioId, Long clienteId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado"));
        if (usuario.getRol() != Usuario.Rol.CLIENTE) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Solo los usuarios CLIENTE pueden vincularse a un cliente");
        }

        Cliente destino = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Cliente no encontrado"));
        if (!Boolean.TRUE.equals(destino.getActivo())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El cliente está inhabilitado");
        }

        usuarioRepository.findByClienteId(clienteId)
                .filter(vinculado -> !vinculado.getId().equals(usuarioId))
                .ifPresent(vinculado -> {
                    throw new NegocioException(HttpStatus.CONFLICT,
                            "El cliente ya está vinculado a otro usuario");
                });

        Cliente anterior = usuario.getCliente();
        if (anterior != null && anterior.getId().equals(clienteId)) {
            return usuario;
        }

        if (anterior != null && clienteTieneHistorial(anterior)) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "No se puede desvincular: el cliente actual tiene ventas o pagos registrados. "
                            + "Revisá su cuenta corriente antes de cambiar la vinculación");
        }

        if (anterior != null) {
            pedidoRepository.reasignarClienteDeUsuario(
                    usuarioId, anterior.getId(), destino);
        }

        usuario.setCliente(destino);
        Usuario guardado = usuarioRepository.save(usuario);
        fotoPerfilService.reconciliarVinculo(guardado, destino);

        if (anterior != null && !clienteTieneHistorial(anterior)) {
            anterior.setActivo(false);
            clienteRepository.save(anterior);
        }
        return guardado;
    }

    /** Un cliente con ventas o pagos nunca se da de baja ni se desvincula automáticamente. */
    private boolean clienteTieneHistorial(Cliente cliente) {
        return ventaRepository.existsByClienteId(cliente.getId())
                || pagoRepository.countByCliente_IdOrVenta_Cliente_Id(
                        cliente.getId(), cliente.getId()) > 0;
    }

    public List<Cliente> listarTodos() {
        return clienteRepository.findByActivoTrueOrderByIdAsc();
    }
    
    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }
    
    public List<Cliente> buscar(String termino) {
        return clienteRepository.buscar(termino);
    }
    
    public List<Cliente> clientesConDeuda() {
        return clienteRepository.findClientesConDeuda();
    }
    
    @Transactional
    public Cliente guardar(Cliente cliente) {
        Cliente guardado = clienteRepository.save(cliente);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clienteId", guardado.getId());
        detalle.put("nombre", guardado.getNombre());
        detalle.put("telefono", guardado.getTelefono());
        detalle.put("email", guardado.getEmail());
        detalle.put("saldoCorriente", guardado.getSaldoCorriente());
        auditoriaService.registrar(AccionAuditoria.CLIENTE_CREADO, "Cliente",
                guardado.getId(),
                "Creó el cliente '" + guardado.getNombre() + "'",
                detalle);
        return guardado;
    }
    
    @Transactional
    public Cliente actualizar(Long id, Cliente clienteActualizado) {
        Cliente cliente = buscarPorId(id);
        Map<String, Object> antes = datosEditablesCliente(cliente);
        cliente.setNombre(clienteActualizado.getNombre());
        cliente.setTelefono(clienteActualizado.getTelefono());
        cliente.setEmail(clienteActualizado.getEmail());
        cliente.setDireccion(clienteActualizado.getDireccion());
        cliente.setCuit(clienteActualizado.getCuit());
        cliente.setCondicionIva(clienteActualizado.getCondicionIva());
        cliente.setObservaciones(clienteActualizado.getObservaciones());
        Cliente guardado = clienteRepository.save(cliente);

        Map<String, Object> despues = datosEditablesCliente(guardado);
        Map<String, Object> cambios = auditoriaService.calcularDiff(antes, despues);
        if (!cambios.isEmpty()) {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("clienteId", guardado.getId());
            detalle.put("nombre", guardado.getNombre());
            detalle.put("cambios", cambios);
            auditoriaService.registrar(AccionAuditoria.CLIENTE_EDITADO, "Cliente",
                    guardado.getId(),
                    "Editó el cliente '" + guardado.getNombre() + "'",
                    detalle);
        }
        return guardado;
    }
    
    @Transactional
    public void eliminar(Long id) {
        Cliente cliente = buscarPorId(id);
        boolean estabaActivo = Boolean.TRUE.equals(cliente.getActivo());
        cliente.setActivo(false);
        clienteRepository.save(cliente);

        if (estabaActivo) {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("clienteId", cliente.getId());
            detalle.put("nombre", cliente.getNombre());
            detalle.put("saldoCorriente", cliente.getSaldoCorriente());
            auditoriaService.registrar(AccionAuditoria.CLIENTE_ELIMINADO, "Cliente",
                    cliente.getId(),
                    "Dio de baja el cliente '" + cliente.getNombre() + "'",
                    detalle);
        }
    }
    
    @Transactional
    public Cliente ajustarSaldo(Long id, BigDecimal monto, String tipo, String metodoPago) {
        return ajustarSaldo(id, monto, tipo, metodoPago, null);
    }

    @Transactional
    public Cliente ajustarSaldo(Long id, BigDecimal monto, String tipo, String metodoPago,
                                Usuario usuario) {
        Cliente cliente = buscarPorId(id);
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }

        BigDecimal saldoActual = cliente.getSaldoCorriente() == null
                ? BigDecimal.ZERO
                : cliente.getSaldoCorriente();

        Cliente resultado;
        if ("sumar".equals(tipo)) {
            cliente.setSaldoCorriente(saldoActual.add(monto));
            resultado = clienteRepository.save(cliente);
        } else if ("restar".equals(tipo)) {
            // El pago se aplica a las ventas abiertas del cliente (más antiguas primero)
            // y descuenta el saldoCorriente exactamente una vez.
            ventaService.registrarPagoCuentaCorriente(id, monto, metodoPago, usuario);
            resultado = buscarPorId(id);
        } else {
            throw new IllegalArgumentException("Tipo de ajuste inválido");
        }

        BigDecimal saldoDespues = resultado.getSaldoCorriente() == null
                ? BigDecimal.ZERO
                : resultado.getSaldoCorriente();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clienteId", resultado.getId());
        detalle.put("clienteNombre", resultado.getNombre());
        detalle.put("tipo", tipo);
        detalle.put("monto", monto);
        detalle.put("metodoPago", metodoPago);
        detalle.put("saldoAntes", saldoActual);
        detalle.put("saldoDespues", saldoDespues);
        String resumen = "sumar".equals(tipo)
                ? "Aumentó el saldo del cliente '" + resultado.getNombre() + "' en $"
                        + formatearMonto(monto)
                : "Registró un pago de $" + formatearMonto(monto)
                        + " a la cuenta del cliente '" + resultado.getNombre() + "'";
        auditoriaService.registrar(AccionAuditoria.CLIENTE_SALDO_AJUSTADO, "Cliente",
                resultado.getId(), resumen, detalle);
        return resultado;
    }

    public List<Pago> pagosPorCliente(Long id) {
        buscarPorId(id);
        return pagoRepository.findPagosPorCliente(id);
    }

    private Map<String, Object> datosEditablesCliente(Cliente cliente) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", cliente.getNombre());
        datos.put("telefono", cliente.getTelefono());
        datos.put("email", cliente.getEmail());
        datos.put("direccion", cliente.getDireccion());
        datos.put("cuit", cliente.getCuit());
        datos.put("condicionIva", cliente.getCondicionIva());
        datos.put("observaciones", cliente.getObservaciones());
        return datos;
    }

    private String formatearMonto(BigDecimal monto) {
        DecimalFormat formato = new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")));
        return formato.format(monto);
    }
}
