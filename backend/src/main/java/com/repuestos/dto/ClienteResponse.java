package com.repuestos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.repuestos.models.Cliente;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClienteResponse(
        Long id,
        String nombre,
        String telefono,
        String email,
        String direccion,
        String cuit,
        String condicionIva,
        BigDecimal saldoCorriente,
        String observaciones,
        Boolean activo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ClienteResponse desde(Cliente cliente, boolean incluirCuentaCorriente) {
        if (cliente == null) {
            return null;
        }
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getTelefono(),
                cliente.getEmail(),
                cliente.getDireccion(),
                cliente.getCuit(),
                cliente.getCondicionIva(),
                incluirCuentaCorriente ? cliente.getSaldoCorriente() : null,
                cliente.getObservaciones(),
                cliente.getActivo(),
                cliente.getCreatedAt(),
                cliente.getUpdatedAt());
    }
}
