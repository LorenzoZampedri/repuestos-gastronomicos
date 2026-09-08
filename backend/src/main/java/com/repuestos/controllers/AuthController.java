package com.repuestos.controllers;

import com.repuestos.models.Usuario;
import com.repuestos.security.JwtUtil;
import com.repuestos.services.UsuarioService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        
        final UserDetails userDetails = usuarioService.loadUserByUsername(request.getUsername());
        Usuario usuario = usuarioService.buscarPorUsername(request.getUsername());
        final String token = jwtUtil.generateToken(userDetails, usuario.getRol().name());
        
        return ResponseEntity.ok(Map.of(
            "token", token,
            "username", usuario.getUsername(),
            "nombre", usuario.getNombre(),
            "rol", usuario.getRol().name()
        ));
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        Usuario usuario = Usuario.builder()
            .username(request.getUsername())
            .passwordHash(request.getPassword())
            .nombre(request.getNombre())
            .email(request.getEmail())
            .rol(Usuario.Rol.valueOf(request.getRol()))
            .build();
        
        Usuario registrado = usuarioService.registrar(usuario);
        return ResponseEntity.ok(Map.of(
            "id", registrado.getId(),
            "username", registrado.getUsername(),
            "nombre", registrado.getNombre(),
            "rol", registrado.getRol().name()
        ));
    }
    
    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }
    
    @Data
    static class RegisterRequest {
        private String username;
        private String password;
        private String nombre;
        private String email;
        private String rol;
    }
}
