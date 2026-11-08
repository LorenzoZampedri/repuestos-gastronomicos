package com.repuestos.dto;

import java.time.Instant;

public record VistaComoResponse(
        String token,
        String username,
        String nombre,
        String rol,
        String fotoUrl,
        Instant expiraEn,
        boolean soloLectura) {
}
