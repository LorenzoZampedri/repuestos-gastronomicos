package com.repuestos.dto;

import java.math.BigDecimal;

/** Estado actual de los precios: cotización y productos desactualizados. */
public record PreciosEstadoResponse(
        BigDecimal cotizacion,
        long productosDesactualizados,
        long productosSinPrecioUsd) {
}
