package com.repuestos.controllers;

import com.repuestos.dto.*;
import com.repuestos.models.Usuario;
import com.repuestos.services.FotoPerfilService;
import com.repuestos.services.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final FotoPerfilService fotoPerfilService;

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

    @PutMapping("/{id}/cliente")
    public ResponseEntity<UsuarioResponse> vincularCliente(
            @PathVariable Long id,
            @Valid @RequestBody VincularClienteRequest request) {
        return ResponseEntity.ok(UsuarioResponse.desde(
                usuarioService.vincularCliente(id, request.clienteId())));
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

    @PutMapping("/{id}/foto")
    public ResponseEntity<UsuarioResponse> cambiarFoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        Usuario actualizado = fotoPerfilService.cambiarFotoUsuario(id, file);
        return ResponseEntity.ok(UsuarioResponse.desde(actualizado));
    }

    @DeleteMapping("/{id}/foto")
    public ResponseEntity<UsuarioResponse> quitarFoto(@PathVariable Long id) {
        Usuario actualizado = fotoPerfilService.quitarFotoUsuario(id);
        return ResponseEntity.ok(UsuarioResponse.desde(actualizado));
    }
}
