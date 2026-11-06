package com.repuestos.dto;

import jakarta.validation.constraints.NotNull;

public record ActivoRequest(
        @NotNull(message = "El estado activo es obligatorio") Boolean activo) {
}
