package com.repuestos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.repuestos.models.Usuario;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponse(
        Long id,
        String username,
        String nombre,
        String email,
        String rol,
        Boolean activo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol().name(),
                usuario.getActivo(),
                usuario.getCreatedAt(),
                usuario.getUpdatedAt());
    }
}
