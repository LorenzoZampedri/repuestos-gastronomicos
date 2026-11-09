package com.repuestos.dto;

public record CargarDeudaResponse(
        ClienteResponse cliente,
        CargoResponse cargo,
        boolean clienteCreado) {
}
