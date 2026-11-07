package com.repuestos.dto;

import java.util.List;

public record PaginaLogsResponse(
        List<LogActividadResponse> content,
        int pagina,
        int totalPaginas,
        long total,
        int size) {
}
