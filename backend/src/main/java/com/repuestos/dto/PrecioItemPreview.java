package com.repuestos.dto;

import java.math.BigDecimal;

/** Fila de la vista previa: producto afectado con precios antes/después. */
public record PrecioItemPreview(
        Long productoId,
        String productoNombre,
        String categoria,
        BigDecimal precioUsdAntes,
        BigDecimal precioUsdDespues,
        BigDecimal precioArsAntes,
        BigDecimal precioArsDespues,
        BigDecimal variacionPorcentaje) {
}
