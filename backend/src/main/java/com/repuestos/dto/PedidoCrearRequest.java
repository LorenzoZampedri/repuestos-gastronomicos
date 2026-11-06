package com.repuestos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PedidoCrearRequest(
        @NotEmpty(message = "El pedido debe tener al menos un producto")
        @Size(max = 50, message = "El pedido no puede tener más de 50 líneas")
        List<@Valid PedidoItemRequest> items,
        @NotBlank(message = "El teléfono es obligatorio")
        @Size(min = 6, max = 20, message = "El teléfono debe tener entre 6 y 20 caracteres")
        String telefono,
        @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres")
        String observaciones) {
}
