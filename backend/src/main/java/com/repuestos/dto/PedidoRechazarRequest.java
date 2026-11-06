package com.repuestos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PedidoRechazarRequest(
        @NotBlank(message = "El motivo de rechazo es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo) {
}
