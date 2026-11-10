package com.repuestos.dto;

import com.repuestos.services.UsuarioUnicidad;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres")
        @Pattern(regexp = UsuarioUnicidad.REGEX_USERNAME,
                message = UsuarioUnicidad.MSG_USERNAME_FORMATO)
        String username,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,
        @Email(message = "El email no es válido")
        @Size(max = 100, message = "El email no puede superar los 100 caracteres")
        String email) {
}
