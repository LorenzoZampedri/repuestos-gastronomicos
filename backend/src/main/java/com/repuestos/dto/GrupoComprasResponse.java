package com.repuestos.dto;

import java.util.List;

public record GrupoComprasResponse(
        long cantidad,
        List<VentaResumenResponse> items) {
}
