package com.repuestos.services;

import com.repuestos.dto.DatosTransferenciaRequest;
import com.repuestos.dto.DatosTransferenciaResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Configuracion;
import com.repuestos.models.ConfiguracionHistorial;
import com.repuestos.models.Producto;
import com.repuestos.repositories.ConfiguracionHistorialRepository;
import com.repuestos.repositories.ConfiguracionRepository;
import com.repuestos.repositories.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    public static final String CLAVE_COTIZACION_USD_ARS = "cotizacion_usd_ars";
    public static final String CLAVE_WHATSAPP_NUMBER = "whatsapp_number";
    public static final String CLAVE_TRANSFERENCIA_CVU = "transferencia_cvu";
    public static final String CLAVE_TRANSFERENCIA_ALIAS = "transferencia_alias";
    public static final String CLAVE_TRANSFERENCIA_TITULAR = "transferencia_titular";
    public static final String COTIZACION_INICIAL = "1000";
    public static final String CVU_INICIAL = "0000003100098765432101";
    public static final String ALIAS_INICIAL = "gastorepuestos.mp";
    public static final String TITULAR_INICIAL = "Repuestos Gastronomicos Once";

    private static final Set<String> CLAVES_TRANSFERENCIA = Set.of(
            CLAVE_TRANSFERENCIA_CVU, CLAVE_TRANSFERENCIA_ALIAS, CLAVE_TRANSFERENCIA_TITULAR);

    private final ConfiguracionRepository configuracionRepository;
    private final ConfiguracionHistorialRepository configuracionHistorialRepository;
    private final ProductoRepository productoRepository;
    private final AuditoriaService auditoriaService;

    public List<Configuracion> listarTodas() {
        return configuracionRepository.findAll();
    }

    public String obtenerValor(String clave) {
        return configuracionRepository.findByClave(clave)
                .map(Configuracion::getValor)
                .orElse(null);
    }

    public BigDecimal getCotizacionUsdArs() {
        return parseCotizacion(obtenerValor(CLAVE_COTIZACION_USD_ARS));
    }

    private BigDecimal parseCotizacion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            BigDecimal cotizacion = new BigDecimal(valor.trim());
            return cotizacion.compareTo(BigDecimal.ZERO) > 0 ? cotizacion : null;
        } catch (NumberFormatException e) {
            log.warn("Cotización inválida configurada: {}", valor);
            return null;
        }
    }

    @Transactional
    public Configuracion guardarOActualizar(String clave, String valor, String descripcion) {
        Configuracion configuracion = configuracionRepository.findByClave(clave)
                .orElseGet(() -> Configuracion.builder().clave(clave).build());
        String valorAnterior = configuracion.getValor();
        configuracion.setValor(valor);
        if (descripcion != null && !descripcion.isBlank()) {
            configuracion.setDescripcion(descripcion);
        }
        Configuracion guardada = configuracionRepository.save(configuracion);
        if (valorAnterior != null && !valorAnterior.equals(valor)) {
            registrarHistorial(clave, valorAnterior, valor);
        }
        if (!esClaveTransferencia(clave) && !Objects.equals(valorAnterior, valor)) {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("clave", clave);
            detalle.put("valorAntes", valorAnterior);
            detalle.put("valorDespues", valor);
            auditoriaService.registrar(AccionAuditoria.CONFIG_EDITADA,
                    "Configuracion", null,
                    "Editó la configuración '" + clave + "': "
                            + mostrarValor(valorAnterior) + " → " + mostrarValor(valor),
                    detalle);
        }
        if (CLAVE_COTIZACION_USD_ARS.equals(clave)) {
            recalcularSiCambioCotizacion(valorAnterior, valor);
        }
        return guardada;
    }

    /**
     * Cuando la cotización USD → ARS cambia de valor (no en el sembrado inicial
     * ni cuando se guarda el mismo número), recalcula en la MISMA transacción el
     * precio en pesos de todos los productos activos con precio USD y registra
     * una sola fila de auditoría. Si el recálculo falla, el cambio de cotización
     * también se revierte.
     */
    private void recalcularSiCambioCotizacion(String valorAnterior, String valorNuevo) {
        if (valorAnterior == null) {
            return;
        }
        BigDecimal anterior = parseCotizacion(valorAnterior);
        BigDecimal nueva = parseCotizacion(valorNuevo);
        if (anterior == null || nueva == null || anterior.compareTo(nueva) == 0) {
            return;
        }
        int cambiados = recalcularPreciosArs(nueva);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clave", CLAVE_COTIZACION_USD_ARS);
        detalle.put("cotizacionAnterior", anterior);
        detalle.put("cotizacionNueva", nueva);
        detalle.put("cantidadProductos", cambiados);
        auditoriaService.registrar(AccionAuditoria.PRECIOS_RECALCULADOS,
                "Configuracion", null,
                "Recalculó " + cambiados + " precios en pesos: cotización "
                        + anterior.toPlainString() + " → " + nueva.toPlainString(),
                detalle);
    }

    /**
     * Sincroniza el precio en pesos de todos los productos activos con precio
     * USD usando la fórmula compartida. Devuelve cuántos precios cambiaron
     * (idempotente: una segunda llamada sin cambios devuelve 0).
     */
    @Transactional
    public int recalcularPreciosArs(BigDecimal cotizacion) {
        if (cotizacion == null) {
            return 0;
        }
        List<Producto> productos = productoRepository.findByActivoTrue();
        List<Producto> modificados = new ArrayList<>();
        for (Producto producto : productos) {
            if (producto.getPrecioUsd() == null
                    || producto.getPrecioUsd().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal esperado = PreciosArs.desdeUsd(producto.getPrecioUsd(), cotizacion);
            BigDecimal actual = producto.getPrecio() == null ? BigDecimal.ZERO : producto.getPrecio();
            if (esperado.compareTo(actual) != 0) {
                producto.setPrecio(esperado);
                modificados.add(producto);
            }
        }
        if (!modificados.isEmpty()) {
            productoRepository.saveAll(modificados);
        }
        return modificados.size();
    }

    public List<ConfiguracionHistorial> listarHistorial(String clave) {
        return configuracionHistorialRepository.findByClaveOrderByFechaDesc(clave);
    }

    public boolean esClaveTransferencia(String clave) {
        return CLAVES_TRANSFERENCIA.contains(clave);
    }

    public DatosTransferenciaResponse obtenerDatosTransferencia() {
        return new DatosTransferenciaResponse(
                valorOpcional(CLAVE_TRANSFERENCIA_CVU),
                valorOpcional(CLAVE_TRANSFERENCIA_ALIAS),
                valorOpcional(CLAVE_TRANSFERENCIA_TITULAR));
    }

    @Transactional
    public DatosTransferenciaResponse guardarDatosTransferencia(DatosTransferenciaRequest request) {
        if (request == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Los datos de transferencia son obligatorios");
        }

        String cvu = request.cvu() == null ? null : request.cvu().trim();
        String alias = request.alias() == null ? null : request.alias().trim();
        String titular = request.titular() == null ? null : request.titular().trim();

        if (cvu == null || !cvu.matches("\\d{22}")) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El CVU debe tener exactamente 22 dígitos numéricos");
        }
        if (alias == null || !alias.matches("[A-Za-z0-9.-]{6,20}")) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El alias debe tener entre 6 y 20 caracteres, sin espacios, y solo puede "
                            + "contener letras, números, puntos y guiones");
        }
        if (titular == null || titular.isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El titular de la cuenta es obligatorio");
        }
        if (titular.length() > 100) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El titular de la cuenta no puede superar los 100 caracteres");
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("cvu", valorOpcional(CLAVE_TRANSFERENCIA_CVU));
        antes.put("alias", valorOpcional(CLAVE_TRANSFERENCIA_ALIAS));
        antes.put("titular", valorOpcional(CLAVE_TRANSFERENCIA_TITULAR));

        guardarOActualizar(CLAVE_TRANSFERENCIA_CVU, cvu,
                "CVU o CBU de la cuenta para transferencias");
        guardarOActualizar(CLAVE_TRANSFERENCIA_ALIAS, alias,
                "Alias de la cuenta para transferencias");
        guardarOActualizar(CLAVE_TRANSFERENCIA_TITULAR, titular,
                "Titular de la cuenta para transferencias");

        DatosTransferenciaResponse respuesta = obtenerDatosTransferencia();
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("cvu", respuesta.cvu());
        despues.put("alias", respuesta.alias());
        despues.put("titular", respuesta.titular());

        Map<String, Object> cambios = auditoriaService.calcularDiff(antes, despues);
        if (!cambios.isEmpty()) {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("antes", antes);
            detalle.put("despues", despues);
            detalle.put("campos", cambios);
            auditoriaService.registrar(AccionAuditoria.CONFIG_TRANSFERENCIA_EDITADA,
                    "Configuracion", null,
                    "Editó los datos de transferencia ("
                            + String.join(", ", etiquetasCamposTransferencia(cambios.keySet()))
                            + ")",
                    detalle);
        }
        return respuesta;
    }

    private String valorOpcional(String clave) {
        String valor = obtenerValor(clave);
        return valor == null || valor.isBlank() ? null : valor;
    }

    private void registrarHistorial(String clave, String valorAnterior, String valorNuevo) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : null;
            configuracionHistorialRepository.save(ConfiguracionHistorial.builder()
                    .clave(clave)
                    .valorAnterior(valorAnterior)
                    .valorNuevo(valorNuevo)
                    .usuario(username)
                    .build());
        } catch (Exception e) {
            log.warn("No se pudo registrar el historial de la clave {}: {}", clave, e.getMessage());
        }
    }

    public void inicializarClaves() {
        if (!configuracionRepository.existsByClave(CLAVE_COTIZACION_USD_ARS)) {
            guardarOActualizar(CLAVE_COTIZACION_USD_ARS, COTIZACION_INICIAL,
                    "Cotización del dólar (USD → ARS) usada para convertir precios");
            log.info("Clave {} creada con valor inicial {}", CLAVE_COTIZACION_USD_ARS, COTIZACION_INICIAL);
        }
        sembrarSiNoExiste(CLAVE_TRANSFERENCIA_CVU, CVU_INICIAL,
                "CVU o CBU de la cuenta para transferencias");
        sembrarSiNoExiste(CLAVE_TRANSFERENCIA_ALIAS, ALIAS_INICIAL,
                "Alias de la cuenta para transferencias");
        sembrarSiNoExiste(CLAVE_TRANSFERENCIA_TITULAR, TITULAR_INICIAL,
                "Titular de la cuenta para transferencias");
    }

    private void sembrarSiNoExiste(String clave, String valor, String descripcion) {
        if (!configuracionRepository.existsByClave(clave)) {
            guardarOActualizar(clave, valor, descripcion);
            log.info("Clave {} creada con valor inicial {}", clave, valor);
        }
    }

    @Transactional
    public void migrarPreciosUsd() {
        BigDecimal cotizacion = getCotizacionUsdArs();
        if (cotizacion == null) {
            return;
        }
        List<Producto> productos = productoRepository.findAll();
        int migrados = 0;
        for (Producto producto : productos) {
            boolean sinUsd = producto.getPrecioUsd() == null || producto.getPrecioUsd().compareTo(BigDecimal.ZERO) <= 0;
            boolean conArs = producto.getPrecio() != null && producto.getPrecio().compareTo(BigDecimal.ZERO) > 0;
            if (sinUsd && conArs) {
                BigDecimal precioUsd = producto.getPrecio()
                        .divide(cotizacion, 2, RoundingMode.HALF_UP);
                producto.setPrecioUsd(precioUsd);
                productoRepository.save(producto);
                migrados++;
            }
        }
        if (migrados > 0) {
            log.info("Migración USD: {} productos recibieron precioUsd (cotización {})", migrados, cotizacion);
        }
    }

    private String mostrarValor(String valor) {
        return valor == null || valor.isBlank() ? "sin valor" : "'" + valor + "'";
    }

    private List<String> etiquetasCamposTransferencia(Set<String> claves) {
        List<String> etiquetas = new ArrayList<>();
        for (String clave : claves) {
            switch (clave) {
                case "cvu" -> etiquetas.add("CVU");
                case "alias" -> etiquetas.add("alias");
                case "titular" -> etiquetas.add("titular");
                default -> etiquetas.add(clave);
            }
        }
        return etiquetas;
    }
}
