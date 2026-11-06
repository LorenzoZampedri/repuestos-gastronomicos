package com.repuestos.dto;

import com.repuestos.models.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioActualizarRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,
        @Email(message = "El email no es válido")
        @Size(max = 100, message = "El email no puede superar los 100 caracteres")
        String email,
        @NotNull(message = "El rol es obligatorio") Usuario.Rol rol,
        @NotNull(message = "El estado activo es obligatorio") Boolean activo) {
}
