package com.repuestos.controllers;

import com.repuestos.dto.ClienteResponse;
import com.repuestos.dto.PagoResponse;
import com.repuestos.models.Cliente;
import com.repuestos.models.Usuario;
import com.repuestos.services.ClienteService;
import com.repuestos.services.UsuarioService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<ClienteResponse>> listarTodos() {
        boolean incluirCuentaCorriente = usuarioService.esAdminActual();
        return ResponseEntity.ok(clienteService.listarTodos().stream()
                .map(cliente -> ClienteResponse.desde(cliente, incluirCuentaCorriente))
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ClienteResponse.desde(
                clienteService.buscarPorId(id), usuarioService.esAdminActual()));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ClienteResponse>> buscar(@RequestParam String q) {
        boolean incluirCuentaCorriente = usuarioService.esAdminActual();
        return ResponseEntity.ok(clienteService.buscar(q).stream()
                .map(cliente -> ClienteResponse.desde(cliente, incluirCuentaCorriente))
                .toList());
    }

    @GetMapping("/deudores")
    public ResponseEntity<List<ClienteResponse>> deudores() {
        return ResponseEntity.ok(clienteService.clientesConDeuda().stream()
                .map(cliente -> ClienteResponse.desde(cliente, true))
                .toList());
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@RequestBody Cliente cliente) {
        return ResponseEntity.ok(ClienteResponse.desde(
                clienteService.guardar(cliente), usuarioService.esAdminActual()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> actualizar(@PathVariable Long id, @RequestBody Cliente cliente) {
        return ResponseEntity.ok(ClienteResponse.desde(
                clienteService.actualizar(id, cliente), usuarioService.esAdminActual()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        clienteService.eliminar(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/saldo")
    public ResponseEntity<ClienteResponse> ajustarSaldo(@PathVariable Long id,
                                                         @RequestBody AjustarSaldoRequest request) {
        Usuario usuario = "restar".equals(request.getTipo())
                ? usuarioService.verificarPasswordActual(request.getPassword())
                : null;
        return ResponseEntity.ok(ClienteResponse.desde(clienteService.ajustarSaldo(
                id, request.getMonto(), request.getTipo(), request.getMetodoPago(), usuario), true));
    }

    @GetMapping("/{id}/pagos")
    public ResponseEntity<List<PagoResponse>> pagosPorCliente(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.pagosPorCliente(id).stream()
                .map(PagoResponse::desde)
                .toList());
    }

    @Data
    static class AjustarSaldoRequest {
        private BigDecimal monto;
        private String tipo; // "sumar" o "restar"
        private String metodoPago; // opcional: EFECTIVO, TARJETA, TRANSFERENCIA, OTRO
        private String password; // requerida para acreditar pagos ("restar")
    }
}
