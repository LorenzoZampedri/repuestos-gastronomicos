package com.repuestos.dto;

import java.math.BigDecimal;

/** Snapshot de un producto dentro de un lote de precios. */
public record LotePreciosItemResponse(
        Long productoId,
        String productoNombre,
        BigDecimal precioUsdAntes,
        BigDecimal precioUsdDespues,
        BigDecimal precioArsAntes,
        BigDecimal precioArsDespues) {
}
