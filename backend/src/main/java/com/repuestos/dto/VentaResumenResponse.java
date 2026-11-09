package com.repuestos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.repuestos.models.Venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VentaResumenResponse(
        Long id,
        LocalDateTime fecha,
        BigDecimal total,
        String estado,
        Integer cantidadItems,
        BigDecimal saldoPendiente) {

    public static VentaResumenResponse desde(Venta venta, boolean incluirCuentaCorriente) {
        return new VentaResumenResponse(
                venta.getId(),
                venta.getFecha(),
                venta.getTotal(),
                venta.getEstado().name(),
                venta.getDetalles() == null ? 0 : venta.getDetalles().size(),
                incluirCuentaCorriente ? venta.getSaldoPendiente() : null);
    }
}
