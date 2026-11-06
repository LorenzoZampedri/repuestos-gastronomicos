package com.repuestos.dto;

import com.repuestos.models.Producto;

import java.math.BigDecimal;

public record ProductoResumenResponse(
        Long id,
        String nombre,
        String codigoBarras,
        BigDecimal precio,
        BigDecimal precioUsd) {

    public static ProductoResumenResponse desde(Producto producto) {
        if (producto == null) {
            return null;
        }
        return new ProductoResumenResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getCodigoBarras(),
                producto.getPrecio(),
                producto.getPrecioUsd());
    }
}
