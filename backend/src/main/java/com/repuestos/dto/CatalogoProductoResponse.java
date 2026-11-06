package com.repuestos.dto;

import com.repuestos.models.Producto;

import java.math.BigDecimal;

public record CatalogoProductoResponse(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        BigDecimal precioUsd,
        Integer stockActual,
        String imagenUrl,
        CategoriaResumenResponse categoria) {

    public static CatalogoProductoResponse desde(Producto producto) {
        return new CatalogoProductoResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getPrecioUsd(),
                producto.getStockActual(),
                producto.getImagenUrl(),
                CategoriaResumenResponse.desde(producto.getCategoria()));
    }
}
