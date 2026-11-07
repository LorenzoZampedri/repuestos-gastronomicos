package com.repuestos.dto;

import com.repuestos.models.LotePrecios;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Resumen de un lote de precios para el historial. */
public record LotePreciosResponse(
        Long id,
        LocalDateTime fecha,
        String usuarioUsername,
        String tipoAjuste,
        BigDecimal valor,
        BigDecimal redondeo,
        String alcance,
        String nota,
        BigDecimal cotizacionUsada,
        int cantidadProductos,
        int cantidadOmitidos,
        String estado,
        String revertidoPorUsername,
        LocalDateTime fechaReversion) {

    public static LotePreciosResponse desde(LotePrecios lote) {
        return new LotePreciosResponse(
                lote.getId(),
                lote.getFecha(),
                lote.getUsuarioUsername(),
                lote.getTipoAjuste(),
                lote.getValor(),
                lote.getRedondeo(),
                lote.getAlcance(),
                lote.getNota(),
                lote.getCotizacionUsada(),
                lote.getCantidadProductos(),
                lote.getCantidadOmitidos(),
                lote.getEstado(),
                lote.getRevertidoPorUsername(),
                lote.getFechaReversion());
    }
}
