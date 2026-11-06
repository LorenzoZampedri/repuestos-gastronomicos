package com.repuestos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PedidoConvertirRequest(
        @NotBlank(message = "El método de pago es obligatorio")
        String metodoPago,
        @Size(max = 50, message = "El pedido no puede tener más de 50 líneas")
        List<@Valid PedidoAjusteRequest> items) {
}
