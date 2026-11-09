package com.repuestos.dto;

public record ComprasClienteResponse(
        Long clienteId,
        GrupoComprasResponse realizadas,
        GrupoComprasResponse pendientes,
        GrupoComprasResponse canceladas) {
}
