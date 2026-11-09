package com.repuestos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteNuevoRequest(
        @NotBlank(message = "El nombre del cliente es obligatorio")
        @Size(min = 2, max = 200, message = "El nombre debe tener entre 2 y 200 caracteres")
        String nombre,
        String telefono,
        String email,
        String cuit) {
}
