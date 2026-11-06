package com.repuestos.dto;

import com.repuestos.models.DetalleVenta;

import java.math.BigDecimal;

public record DetalleVentaResponse(
        Long id,
        ProductoResumenResponse producto,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal) {

    public static DetalleVentaResponse desde(DetalleVenta detalle) {
        return new DetalleVentaResponse(
                detalle.getId(),
                ProductoResumenResponse.desde(detalle.getProducto()),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getSubtotal());
    }
}
