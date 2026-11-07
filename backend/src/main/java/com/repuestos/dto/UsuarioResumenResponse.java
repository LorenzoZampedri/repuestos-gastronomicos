package com.repuestos.dto;

import com.repuestos.models.Usuario;

public record UsuarioResumenResponse(
        Long id,
        String username,
        String nombre,
        String rol,
        String fotoUrl) {

    public static UsuarioResumenResponse desde(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return new UsuarioResumenResponse(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombre(),
                usuario.getRol().name(),
                usuario.getFotoUrl());
    }
}
