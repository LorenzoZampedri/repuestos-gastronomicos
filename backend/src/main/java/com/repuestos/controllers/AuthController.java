package com.repuestos.controllers;

import com.repuestos.dto.CambioPasswordRequest;
import com.repuestos.dto.LoginRequest;
import com.repuestos.dto.RegisterRequest;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Usuario;
import com.repuestos.security.JwtUtil;
import com.repuestos.services.AuditoriaService;
import com.repuestos.services.FotoPerfilService;
import com.repuestos.services.NegocioException;
import com.repuestos.services.UsuarioService;
import com.repuestos.services.VistaComoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;
    private final AuditoriaService auditoriaService;
    private final FotoPerfilService fotoPerfilService;
    private final VistaComoService vistaComoService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException ex) {
            auditoriaService.registrarLoginFallido(request.username(), "usuario inactivo");
            throw new NegocioException(HttpStatus.FORBIDDEN,
                    "Tu usuario está desactivado. Contactá al administrador.");
        } catch (AuthenticationException ex) {
            auditoriaService.registrarLoginFallido(request.username(), "credenciales inválidas");
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
        }

        final UserDetails userDetails = usuarioService.loadUserByUsername(request.username());
        Usuario usuario = usuarioService.buscarPorUsername(request.username());
        final String token = jwtUtil.generateToken(userDetails, usuario.getRol().name());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("username", usuario.getUsername());
        detalle.put("rol", usuario.getRol().name());
        auditoriaService.registrarComo(AccionAuditoria.LOGIN_OK, usuario, "Usuario",
                usuario.getId(),
                "Inició sesión (" + usuario.getRol().name() + ")", detalle);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", usuario.getUsername(),
                "nombre", usuario.getNombre(),
                "rol", usuario.getRol().name(),
                "fotoUrl", usuario.getFotoUrl() != null ? usuario.getFotoUrl() : ""));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        Usuario registrado = usuarioService.registrarCliente(
                request.username(), request.password(), request.nombre(), request.email());

        return ResponseEntity.ok(Map.of(
                "id", registrado.getId(),
                "username", registrado.getUsername(),
                "nombre", registrado.getNombre(),
                "rol", registrado.getRol().name()));
    }

    @PostMapping("/vista-como/fin")
    public ResponseEntity<Void> finalizarVistaComo() {
        vistaComoService.finalizar();
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/password")
    public ResponseEntity<?> cambiarPassword(@Valid @RequestBody CambioPasswordRequest request) {
        usuarioService.cambiarPasswordActual(request.passwordActual(), request.passwordNueva());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
    }

    @PutMapping("/foto")
    public ResponseEntity<?> cambiarFotoPropia(@RequestParam("file") MultipartFile file) {
        Usuario usuario = usuarioService.usuarioActual();
        Usuario actualizado = fotoPerfilService.cambiarFotoUsuario(usuario.getId(), file);
        return ResponseEntity.ok(Map.of("fotoUrl", actualizado.getFotoUrl() != null ? actualizado.getFotoUrl() : ""));
    }

    @DeleteMapping("/foto")
    public ResponseEntity<?> quitarFotoPropia() {
        Usuario usuario = usuarioService.usuarioActual();
        Usuario actualizado = fotoPerfilService.quitarFotoUsuario(usuario.getId());
        return ResponseEntity.ok(Map.of("fotoUrl", ""));
    }
}
