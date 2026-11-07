package com.repuestos.dto;

import java.util.List;

/** Resultado de la reversión de un lote: qué se restauró y qué se omitió. */
public record PreciosRevertirResponse(
        int revertidos,
        List<PrecioExcluido> omitidos) {
}
