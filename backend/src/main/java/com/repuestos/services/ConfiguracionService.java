package com.repuestos.services;

import com.repuestos.models.Configuracion;
import com.repuestos.models.Producto;
import com.repuestos.repositories.ConfiguracionRepository;
import com.repuestos.repositories.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    public static final String CLAVE_COTIZACION_USD_ARS = "cotizacion_usd_ars";
    public static final String CLAVE_WHATSAPP_NUMBER = "whatsapp_number";
    public static final String COTIZACION_INICIAL = "1000";

    private final ConfiguracionRepository configuracionRepository;
    private final ProductoRepository productoRepository;

    public List<Configuracion> listarTodas() {
        return configuracionRepository.findAll();
    }

    public String obtenerValor(String clave) {
        return configuracionRepository.findByClave(clave)
                .map(Configuracion::getValor)
                .orElse(null);
    }

    public BigDecimal getCotizacionUsdArs() {
        String valor = obtenerValor(CLAVE_COTIZACION_USD_ARS);
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

    public Configuracion guardarOActualizar(String clave, String valor, String descripcion) {
        Configuracion configuracion = configuracionRepository.findByClave(clave)
                .orElseGet(() -> Configuracion.builder().clave(clave).build());
        configuracion.setValor(valor);
        if (descripcion != null && !descripcion.isBlank()) {
            configuracion.setDescripcion(descripcion);
        }
        return configuracionRepository.save(configuracion);
    }

    public void inicializarClaves() {
        if (!configuracionRepository.existsByClave(CLAVE_COTIZACION_USD_ARS)) {
            guardarOActualizar(CLAVE_COTIZACION_USD_ARS, COTIZACION_INICIAL,
                    "Cotización del dólar (USD → ARS) usada para convertir precios");
            log.info("Clave {} creada con valor inicial {}", CLAVE_COTIZACION_USD_ARS, COTIZACION_INICIAL);
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
}
