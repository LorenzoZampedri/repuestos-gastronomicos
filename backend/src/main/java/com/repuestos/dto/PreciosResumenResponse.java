package com.repuestos.dto;

import java.math.BigDecimal;

/** Resumen numérico de una vista previa o aplicación masiva de precios. */
public record PreciosResumenResponse(
        int productosAfectados,
        int productosExcluidos,
        BigDecimal variacionPromedio,
        BigDecimal variacionMinima,
        BigDecimal variacionMaxima,
        boolean variacionAlta) {
}
