package com.repuestos.controllers;

import com.repuestos.dto.CargarDeudaRequest;
import com.repuestos.dto.CargarDeudaResponse;
import com.repuestos.dto.CargoResponse;
import com.repuestos.dto.ClienteResponse;
import com.repuestos.dto.ComprasClienteResponse;
import com.repuestos.dto.PagoResponse;
import com.repuestos.models.Cliente;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.services.ClienteService;
import com.repuestos.services.CuentaCorrienteService;
import com.repuestos.services.FotoPerfilService;
import com.repuestos.services.UsuarioService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final CuentaCorrienteService cuentaCorrienteService;
    private final UsuarioService usuarioService;
    private final FotoPerfilService fotoPerfilService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<List<ClienteResponse>> listarTodos() {
        boolean incluirCuentaCorriente = usuarioService.esAdminActual();
        Set<Long> idsConCuenta = new HashSet<>(usuarioRepository.findClienteIdsConCuenta());
        return ResponseEntity.ok(clienteService.listarTodos().stream()
                .map(cliente -> ClienteResponse.desde(cliente, incluirCuentaCorriente, idsConCuenta.contains(cliente.getId())))
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long id) {
        Cliente cliente = clienteService.buscarPorId(id);
        boolean cuentaVinculada = usuarioRepository.existsByClienteId(id);
        return ResponseEntity.ok(ClienteResponse.desde(
                cliente, usuarioService.esAdminActual(), cuentaVinculada));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ClienteResponse>> buscar(@RequestParam String q) {
        boolean incluirCuentaCorriente = usuarioService.esAdminActual();
        Set<Long> idsConCuenta = new HashSet<>(usuarioRepository.findClienteIdsConCuenta());
        return ResponseEntity.ok(clienteService.buscar(q).stream()
                .map(cliente -> ClienteResponse.desde(cliente, incluirCuentaCorriente, idsConCuenta.contains(cliente.getId())))
                .toList());
    }

    @GetMapping("/deudores")
    public ResponseEntity<List<ClienteResponse>> deudores() {
        Set<Long> idsConCuenta = new HashSet<>(usuarioRepository.findClienteIdsConCuenta());
        return ResponseEntity.ok(clienteService.clientesConDeuda().stream()
                .map(cliente -> ClienteResponse.desde(cliente, true, idsConCuenta.contains(cliente.getId())))
                .toList());
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@RequestBody Cliente cliente) {
        Cliente guardado = clienteService.guardar(cliente);
        boolean cuentaVinculada = usuarioRepository.existsByClienteId(guardado.getId());
        return ResponseEntity.ok(ClienteResponse.desde(
                guardado, usuarioService.esAdminActual(), cuentaVinculada));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> actualizar(@PathVariable Long id, @RequestBody Cliente cliente) {
        Cliente guardado = clienteService.actualizar(id, cliente);
        boolean cuentaVinculada = usuarioRepository.existsByClienteId(id);
        return ResponseEntity.ok(ClienteResponse.desde(
                guardado, usuarioService.esAdminActual(), cuentaVinculada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        clienteService.eliminar(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/foto")
    public ResponseEntity<ClienteResponse> cambiarFoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        Cliente actualizado = fotoPerfilService.cambiarFotoCliente(id, file);
        boolean cuentaVinculada = usuarioRepository.existsByClienteId(id);
        return ResponseEntity.ok(ClienteResponse.desde(actualizado, usuarioService.esAdminActual(), cuentaVinculada));
    }

    @DeleteMapping("/{id}/foto")
    public ResponseEntity<ClienteResponse> quitarFoto(@PathVariable Long id) {
        Cliente actualizado = fotoPerfilService.quitarFotoCliente(id);
        boolean cuentaVinculada = usuarioRepository.existsByClienteId(id);
        return ResponseEntity.ok(ClienteResponse.desde(actualizado, usuarioService.esAdminActual(), cuentaVinculada));
    }

    @PostMapping("/cargar-deuda")
    public ResponseEntity<CargarDeudaResponse> cargarDeuda(
            @Valid @RequestBody CargarDeudaRequest request) {
        return ResponseEntity.ok(cuentaCorrienteService.cargarDeuda(request));
    }

    @GetMapping("/{id}/cargos")
    public ResponseEntity<List<CargoResponse>> cargosPorCliente(@PathVariable Long id) {
        return ResponseEntity.ok(cuentaCorrienteService.cargosDeCliente(id));
    }

    @GetMapping("/{id}/compras")
    public ResponseEntity<ComprasClienteResponse> comprasPorCliente(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limite) {
        return ResponseEntity.ok(cuentaCorrienteService.comprasDeCliente(
                id, limite, usuarioService.esAdminActual()));
    }

    @PatchMapping("/{id}/saldo")
    public ResponseEntity<ClienteResponse> ajustarSaldo(@PathVariable Long id,
                                                         @RequestBody AjustarSaldoRequest request) {
        Usuario usuario = "restar".equals(request.getTipo())
                ? usuarioService.verificarPasswordActual(request.getPassword())
                : usuarioService.usuarioActual();
        Cliente cliente = clienteService.ajustarSaldo(
                id, request.getMonto(), request.getTipo(), request.getMetodoPago(), usuario);
        boolean cuentaVinculada = usuarioRepository.existsByClienteId(id);
        return ResponseEntity.ok(ClienteResponse.desde(cliente, true, cuentaVinculada));
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
