package com.repuestos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PedidoAjusteRequest(
        @NotNull(message = "La línea del pedido es obligatoria")
        Long detalleId,
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 0, message = "La cantidad no puede ser negativa")
        @Max(value = 9999, message = "La cantidad máxima es 9999")
        Integer cantidad) {
}
