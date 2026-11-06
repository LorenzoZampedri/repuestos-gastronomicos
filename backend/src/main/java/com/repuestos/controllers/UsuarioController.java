package com.repuestos.controllers;

import com.repuestos.dto.ActivoRequest;
import com.repuestos.dto.ResetPasswordRequest;
import com.repuestos.dto.UsuarioActualizarRequest;
import com.repuestos.dto.UsuarioCrearRequest;
import com.repuestos.dto.UsuarioResponse;
import com.repuestos.models.Usuario;
import com.repuestos.services.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listar(
            @RequestParam(required = false) Usuario.Rol rol,
            @RequestParam(required = false) Boolean activo) {
        List<UsuarioResponse> usuarios = usuarioService.listar(rol, activo).stream()
                .map(UsuarioResponse::desde)
                .toList();
        return ResponseEntity.ok(usuarios);
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioCrearRequest request) {
        return ResponseEntity.ok(UsuarioResponse.desde(usuarioService.crear(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioActualizarRequest request) {
        Usuario actualizado = usuarioService.actualizar(id, request, usuarioService.usernameActual());
        return ResponseEntity.ok(UsuarioResponse.desde(actualizado));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        usuarioService.resetPassword(id, request.passwordNueva());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/activo")
    public ResponseEntity<UsuarioResponse> cambiarActivo(
            @PathVariable Long id,
            @Valid @RequestBody ActivoRequest request) {
        Usuario actualizado = usuarioService.cambiarActivo(
                id, request.activo(), usuarioService.usernameActual());
        return ResponseEntity.ok(UsuarioResponse.desde(actualizado));
    }
}
