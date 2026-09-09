package com.repuestos.controllers;

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
    public ResponseEntity<List<Venta>> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin) {
        return ResponseEntity.ok(ventaService.listarVentas(inicio, fin));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Venta> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ventaService.buscarPorId(id));
    }
    
    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Venta>> porCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(ventaService.ventasPorCliente(clienteId));
    }
    
    @GetMapping("/pendientes")
    public ResponseEntity<List<Venta>> pendientes() {
        return ResponseEntity.ok(ventaService.ventasPendientes());
    }
    
    @PostMapping
    public ResponseEntity<Venta> crear(@RequestBody CrearVentaRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioService.buscarPorUsername(username);
        
        Venta venta = Venta.builder()
            .usuario(usuario)
            .observaciones(request.getObservaciones())
            .build();
        
        Venta creada = ventaService.crearVenta(venta, request.getDetalles(), request.getClienteId());
        return ResponseEntity.ok(creada);
    }
    
    @PostMapping("/{id}/pago")
    public ResponseEntity<Pago> registrarPago(@PathVariable Long id, @RequestBody PagoRequest request) {
        Pago pago = Pago.builder()
            .monto(request.getMonto())
            .metodoPago(Pago.MetodoPago.valueOf(request.getMetodoPago()))
            .moneda(request.getMoneda())
            .referencia(request.getReferencia())
            .build();
        
        return ResponseEntity.ok(ventaService.registrarPago(id, pago));
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
    }
    
    @Data
    static class PagoRequest {
        private BigDecimal monto;
        private String metodoPago;
        private String moneda;
        private String referencia;
    }
}
