package com.repuestos.dto;

import java.math.BigDecimal;
import java.util.List;

/** Parámetros de un ajuste masivo, normalizados por el backend. */
public record PreciosParametrosResponse(
        String tipo,
        BigDecimal valor,
        BigDecimal redondeo,
        String alcanceTipo,
        List<Long> categoriaIds,
        List<Long> productoIds,
        String alcanceDescripcion,
        String nota) {
}
