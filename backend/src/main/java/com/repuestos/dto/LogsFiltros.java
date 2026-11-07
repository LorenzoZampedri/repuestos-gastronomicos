package com.repuestos.dto;

import java.time.LocalDate;

public record LogsFiltros(
        int page,
        int size,
        LocalDate desde,
        LocalDate hasta,
        String usuario,
        String rol,
        String modulo,
        String accion,
        String resultado,
        String q) {
}
