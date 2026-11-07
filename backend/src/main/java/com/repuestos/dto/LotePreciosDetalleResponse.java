package com.repuestos.dto;

import com.repuestos.models.LotePrecios;
import com.repuestos.models.LotePreciosItem;

import java.util.List;

/** Detalle de un lote con sus items por producto. */
public record LotePreciosDetalleResponse(
        LotePreciosResponse lote,
        List<LotePreciosItemResponse> items) {

    public static LotePreciosDetalleResponse desde(LotePrecios lote, List<LotePreciosItem> items) {
        return new LotePreciosDetalleResponse(
                LotePreciosResponse.desde(lote),
                items.stream()
                        .map(item -> new LotePreciosItemResponse(
                                item.getProductoId(),
                                item.getProductoNombre(),
                                item.getPrecioUsdAntes(),
                                item.getPrecioUsdDespues(),
                                item.getPrecioArsAntes(),
                                item.getPrecioArsDespues()))
                        .toList());
    }
}
