package com.repuestos.dto;

import com.repuestos.models.Pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoResponse(
        Long id,
        BigDecimal monto,
        String metodoPago,
        String moneda,
        LocalDateTime fecha,
        String referencia,
        UsuarioResumenResponse usuario) {

    public static PagoResponse desde(Pago pago) {
        return new PagoResponse(
                pago.getId(),
                pago.getMonto(),
                pago.getMetodoPago().name(),
                pago.getMoneda(),
                pago.getFecha(),
                pago.getReferencia(),
                UsuarioResumenResponse.desde(pago.getUsuario()));
    }
}
