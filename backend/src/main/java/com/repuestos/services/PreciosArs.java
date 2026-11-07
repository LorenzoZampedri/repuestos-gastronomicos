package com.repuestos.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fórmula única de conversión de precio en dólares a pesos con la cotización
 * vigente. Toda la aplicación (guardado de productos, actualización masiva y
 * recálculo al cambiar la cotización) pasa por acá para no duplicar el cálculo.
 */
public final class PreciosArs {

    public static final int ESCALA = 2;

    private PreciosArs() {
    }

    /** Precio en ARS = USD × cotización, con 2 decimales y HALF_UP. */
    public static BigDecimal desdeUsd(BigDecimal precioUsd, BigDecimal cotizacion) {
        if (precioUsd == null || cotizacion == null) {
            return null;
        }
        return precioUsd.multiply(cotizacion).setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
