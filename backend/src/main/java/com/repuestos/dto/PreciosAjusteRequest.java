package com.repuestos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body común de vista previa y aplicación de una actualización masiva de
 * precios. En la aplicación se agregan {@code token} y
 * {@code confirmarVariacionAlta}.
 */
public record PreciosAjusteRequest(
        @NotBlank(message = "El tipo de ajuste es obligatorio")
        String tipo,

        @NotNull(message = "El valor del ajuste es obligatorio")
        BigDecimal valor,

        String redondeo,

        @NotNull(message = "El alcance es obligatorio")
        Alcance alcance,

        @Size(max = 200, message = "La nota no puede superar los 200 caracteres")
        String nota,

        String token,

        Boolean confirmarVariacionAlta) {

    public record Alcance(
            @NotBlank(message = "El tipo de alcance es obligatorio")
            String tipo,
            List<Long> categoriaIds,
            List<Long> productoIds) {
    }
}
