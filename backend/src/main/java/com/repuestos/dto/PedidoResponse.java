package com.repuestos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.repuestos.models.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoResponse(
        Long id,
        ClientePedidoResponse cliente,
        UsuarioResumenResponse usuario,
        LocalDateTime fecha,
        String estado,
        String observaciones,
        String motivoRechazo,
        BigDecimal totalEstimado,
        Long ventaId,
        UsuarioResumenResponse resueltoPor,
        LocalDateTime fechaResolucion,
        List<PedidoDetalleResponse> detalles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PedidoResponse desde(Pedido pedido, boolean incluirDatosStaff,
                                       boolean incluirSaldo, boolean incluirStock) {
        return new PedidoResponse(
                pedido.getId(),
                incluirDatosStaff
                        ? ClientePedidoResponse.desde(pedido.getCliente(), incluirSaldo) : null,
                incluirDatosStaff
                        ? UsuarioResumenResponse.desde(pedido.getUsuario()) : null,
                pedido.getFecha(),
                pedido.getEstado().name(),
                pedido.getObservaciones(),
                pedido.getMotivoRechazo(),
                pedido.getTotalEstimado(),
                pedido.getVenta() == null ? null : pedido.getVenta().getId(),
                incluirDatosStaff
                        ? UsuarioResumenResponse.desde(pedido.getResueltoPor()) : null,
                pedido.getFechaResolucion(),
                pedido.getDetalles().stream()
                        .map(detalle -> PedidoDetalleResponse.desde(detalle, incluirStock))
                        .toList(),
                pedido.getCreatedAt(),
                pedido.getUpdatedAt());
    }
}
