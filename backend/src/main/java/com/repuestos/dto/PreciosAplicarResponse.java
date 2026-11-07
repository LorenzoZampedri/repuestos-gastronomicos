package com.repuestos.dto;

import java.math.BigDecimal;
import java.util.List;

/** Respuesta de la aplicación de un ajuste masivo de precios. */
public record PreciosAplicarResponse(
        Long loteId,
        BigDecimal cotizacion,
        PreciosResumenResponse resumen,
        List<PrecioExcluido> excluidos) {
}
