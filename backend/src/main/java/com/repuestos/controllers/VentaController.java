package com.repuestos.controllers;

import com.repuestos.dto.PagoResponse;
import com.repuestos.dto.VentaResponse;
import com.repuestos.models.DetalleVenta;
import com.repuestos.models.Pago;
import com.repuestos.models.Venta;
import com.repuestos.models.Usuario;
import com.repuestos.services.VentaService;
import com.repuestos.services.UsuarioService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;
    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<VentaResponse>> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin) {
        boolean incluirCuentaCorriente = usuarioService.esAdminActual();
        return ResponseEntity.ok(ventaService.listarVentas(inicio, fin).stream()
                .map(venta -> VentaResponse.desde(venta, incluirCuentaCorriente))
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(VentaResponse.desde(
                ventaService.buscarPorId(id), usuarioService.esAdminActual()));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<VentaResponse>> porCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(ventaService.ventasPorCliente(clienteId).stream()
                .map(venta -> VentaResponse.desde(venta, true))
                .toList());
    }

    @GetMapping("/pendientes")
    public ResponseEntity<List<VentaResponse>> pendientes() {
        return ResponseEntity.ok(ventaService.ventasPendientes().stream()
                .map(venta -> VentaResponse.desde(venta, true))
                .toList());
    }

    @PostMapping
    public ResponseEntity<VentaResponse> crear(@RequestBody CrearVentaRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioService.buscarPorUsername(username);

        Venta venta = Venta.builder()
            .usuario(usuario)
            .observaciones(request.getObservaciones())
            .build();

        Venta creada = ventaService.crearVentaConPago(
                venta, request.getDetalles(), request.getClienteId(), request.getMetodoPago());
        return ResponseEntity.ok(VentaResponse.desde(creada, usuarioService.esAdminActual()));
    }

    @PostMapping("/{id}/pago")
    public ResponseEntity<PagoResponse> registrarPago(@PathVariable Long id, @RequestBody PagoRequest request) {
        Usuario usuario = usuarioService.verificarPasswordActual(request.getPassword());
        Pago pago = Pago.builder()
            .monto(request.getMonto())
            .metodoPago(Pago.MetodoPago.valueOf(request.getMetodoPago()))
            .moneda(request.getMoneda())
            .referencia(request.getReferencia())
            .usuario(usuario)
            .build();

        return ResponseEntity.ok(PagoResponse.desde(ventaService.registrarPago(id, pago)));
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<Void> anular(@PathVariable Long id) {
        ventaService.anularVenta(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/resumen")
    public ResponseEntity<Map<String, BigDecimal>> resumen() {
        return ResponseEntity.ok(Map.of(
            "ventasDelDia", ventaService.ventasDelDia(),
            "totalVentas", ventaService.totalVentas()
        ));
    }

    @Data
    static class CrearVentaRequest {
        private Long clienteId;
        private List<DetalleVenta> detalles;
        private String observaciones;
        private String metodoPago;
    }

    @Data
    static class PagoRequest {
        private BigDecimal monto;
        private String metodoPago;
        private String moneda;
        private String referencia;
        private String password;
    }
}
