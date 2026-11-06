package com.repuestos.controllers;

import com.repuestos.dto.CambioPasswordRequest;
import com.repuestos.dto.LoginRequest;
import com.repuestos.dto.RegisterRequest;
import com.repuestos.models.Usuario;
import com.repuestos.security.JwtUtil;
import com.repuestos.services.NegocioException;
import com.repuestos.services.UsuarioService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException ex) {
            throw new NegocioException(HttpStatus.FORBIDDEN,
                    "Tu usuario está desactivado. Contactá al administrador.");
        } catch (AuthenticationException ex) {
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
        }

        final UserDetails userDetails = usuarioService.loadUserByUsername(request.username());
        Usuario usuario = usuarioService.buscarPorUsername(request.username());
        final String token = jwtUtil.generateToken(userDetails, usuario.getRol().name());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", usuario.getUsername(),
                "nombre", usuario.getNombre(),
                "rol", usuario.getRol().name()));
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

    @PutMapping("/password")
    public ResponseEntity<?> cambiarPassword(@Valid @RequestBody CambioPasswordRequest request) {
        usuarioService.cambiarPasswordActual(request.passwordActual(), request.passwordNueva());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
    }
}
