package com.repuestos.controllers;

import com.repuestos.dto.PedidoContactoResponse;
import com.repuestos.dto.PedidoCrearRequest;
import com.repuestos.dto.PedidoRechazarRequest;
import com.repuestos.dto.PedidoResponse;
import com.repuestos.models.Pedido;
import com.repuestos.services.PedidoService;
import com.repuestos.services.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;
    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody PedidoCrearRequest request) {
        return ResponseEntity.ok(pedidoService.crear(usuarioService.usuarioActual(), request));
    }

    @GetMapping("/mios")
    public ResponseEntity<List<PedidoResponse>> listarMios() {
        return ResponseEntity.ok(pedidoService.listarMios(usuarioService.usuarioActual()));
    }

    @GetMapping("/mios/contacto")
    public ResponseEntity<PedidoContactoResponse> contacto() {
        return ResponseEntity.ok(pedidoService.contacto(usuarioService.usuarioActual()));
    }

    @GetMapping("/mios/{id}")
    public ResponseEntity<PedidoResponse> obtenerMio(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.obtenerMio(id, usuarioService.usuarioActual()));
    }

    @PutMapping("/mios/{id}/cancelar")
    public ResponseEntity<PedidoResponse> cancelarMio(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.cancelarMio(id, usuarioService.usuarioActual()));
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponse>> listar(
            @RequestParam(required = false) Pedido.EstadoPedido estado) {
        Pedido.EstadoPedido filtro = estado == null ? Pedido.EstadoPedido.PENDIENTE : estado;
        return ResponseEntity.ok(pedidoService.listarStaff(
                filtro, usuarioService.esAdminActual()));
    }

    @GetMapping("/pendientes/count")
    public ResponseEntity<Map<String, Long>> contarPendientes() {
        return ResponseEntity.ok(Map.of("count", pedidoService.contarPendientes()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.obtenerStaff(
                id, usuarioService.esAdminActual()));
    }

    @PostMapping("/{id}/convertir")
    public ResponseEntity<PedidoResponse> convertir(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.convertir(
                id, usuarioService.usuarioActual(), usuarioService.esAdminActual()));
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<PedidoResponse> rechazar(
            @PathVariable Long id,
            @Valid @RequestBody PedidoRechazarRequest request) {
        return ResponseEntity.ok(pedidoService.rechazar(
                id, request, usuarioService.usuarioActual(), usuarioService.esAdminActual()));
    }
}
