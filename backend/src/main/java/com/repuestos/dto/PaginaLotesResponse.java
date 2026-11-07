package com.repuestos.dto;

import java.util.List;

/** Página de lotes de precios (más recientes primero). */
public record PaginaLotesResponse(
        List<LotePreciosResponse> content,
        int number,
        int totalPages,
        long totalElements,
        int size) {
}
