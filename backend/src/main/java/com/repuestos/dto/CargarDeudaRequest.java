package com.repuestos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CargarDeudaRequest(
        Long clienteId,
        @Valid ClienteNuevoRequest clienteNuevo,
        @NotNull(message = "El monto es obligatorio")
        BigDecimal monto,
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 3, max = 300, message = "El motivo debe tener entre 3 y 300 caracteres")
        String motivo) {
}
