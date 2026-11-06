package com.repuestos.dto;

import com.repuestos.models.DetallePedido;

import java.math.BigDecimal;

public record PedidoDetalleResponse(
        Long id,
        Long productoId,
        String nombreProducto,
        Integer cantidad,
        BigDecimal precioUnitarioEstimado,
        BigDecimal subtotalEstimado,
        Integer stockActual,
        Boolean productoActivo) {

    public static PedidoDetalleResponse desde(DetallePedido detalle, boolean incluirStock) {
        BigDecimal subtotal = detalle.getPrecioUnitarioEstimado()
                .multiply(BigDecimal.valueOf(detalle.getCantidad()));
        return new PedidoDetalleResponse(
                detalle.getId(),
                detalle.getProducto() == null ? null : detalle.getProducto().getId(),
                detalle.getNombreProducto(),
                detalle.getCantidad(),
                detalle.getPrecioUnitarioEstimado(),
                subtotal,
                incluirStock && detalle.getProducto() != null
                        ? detalle.getProducto().getStockActual() : null,
                incluirStock && detalle.getProducto() != null
                        ? detalle.getProducto().getActivo() : null);
    }
}
