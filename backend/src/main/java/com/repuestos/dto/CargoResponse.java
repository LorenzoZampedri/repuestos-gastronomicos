package com.repuestos.dto;

import com.repuestos.models.CargoCuentaCorriente;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CargoResponse(
        Long id,
        BigDecimal monto,
        String motivo,
        UsuarioResumenResponse usuario,
        LocalDateTime fecha,
        BigDecimal saldoAntes,
        BigDecimal saldoDespues) {

    public static CargoResponse desde(CargoCuentaCorriente cargo) {
        return new CargoResponse(
                cargo.getId(),
                cargo.getMonto(),
                cargo.getMotivo(),
                UsuarioResumenResponse.desde(cargo.getUsuario()),
                cargo.getFecha(),
                cargo.getSaldoAntes(),
                cargo.getSaldoDespues());
    }
}
