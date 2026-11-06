package com.repuestos.dto;

import jakarta.validation.constraints.NotNull;

public record VincularClienteRequest(
        @NotNull(message = "El cliente es obligatorio") Long clienteId) {
}
