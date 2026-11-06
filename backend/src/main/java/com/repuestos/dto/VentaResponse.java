package com.repuestos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.repuestos.models.Venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VentaResponse(
        Long id,
        ClienteResponse cliente,
        UsuarioResumenResponse usuario,
        LocalDateTime fecha,
        BigDecimal total,
        String estado,
        String observaciones,
        List<DetalleVentaResponse> detalles,
        List<PagoResponse> pagos,
        BigDecimal montoPagado,
        BigDecimal saldoPendiente,
        LocalDateTime createdAt) {

    public static VentaResponse desde(Venta venta, boolean incluirCuentaCorriente) {
        return new VentaResponse(
                venta.getId(),
                ClienteResponse.desde(venta.getCliente(), incluirCuentaCorriente),
                UsuarioResumenResponse.desde(venta.getUsuario()),
                venta.getFecha(),
                venta.getTotal(),
                venta.getEstado().name(),
                venta.getObservaciones(),
                venta.getDetalles().stream().map(DetalleVentaResponse::desde).toList(),
                venta.getPagos().stream().map(PagoResponse::desde).toList(),
                incluirCuentaCorriente ? venta.getMontoPagado() : null,
                incluirCuentaCorriente ? venta.getSaldoPendiente() : null,
                venta.getCreatedAt());
    }
}
