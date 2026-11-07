package com.repuestos.dto;

import java.math.BigDecimal;
import java.util.List;

/** Respuesta de la vista previa obligatoria antes de aplicar un ajuste. */
public record PreciosVistaPreviaResponse(
        String token,
        BigDecimal cotizacion,
        PreciosParametrosResponse parametros,
        PreciosResumenResponse resumen,
        List<PrecioItemPreview> items,
        List<PrecioExcluido> excluidos,
        long pedidosPendientesAfectados) {
}
