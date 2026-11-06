package com.repuestos.dto;

import com.repuestos.models.Cliente;

import java.math.BigDecimal;

public record ClientePedidoResponse(
        Long id,
        String nombre,
        String telefono,
        String email,
        String direccion,
        BigDecimal saldoCorriente) {

    public static ClientePedidoResponse desde(Cliente cliente, boolean incluirSaldo) {
        if (cliente == null) {
            return null;
        }
        return new ClientePedidoResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getTelefono(),
                cliente.getEmail(),
                cliente.getDireccion(),
                incluirSaldo ? cliente.getSaldoCorriente() : null);
    }
}
