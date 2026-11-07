package com.repuestos.dto;

/** Producto del alcance que no recibirá el ajuste, con el motivo. */
public record PrecioExcluido(
        Long productoId,
        String productoNombre,
        String motivo) {
}
