package com.repuestos.controllers;

import com.repuestos.models.Usuario;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.services.AvatarStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.security.Principal;

@RestController
@RequestMapping("/api/avatares")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarStorageService avatarStorageService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/{archivo}")
    public ResponseEntity<Resource> servir(@PathVariable String archivo, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = principal.getName();
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Path path = avatarStorageService.cargar(archivo);
        if (path == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String url = "/api/avatares/" + archivo;

        if (usuario.getRol() == Usuario.Rol.ADMIN || usuario.getRol() == Usuario.Rol.EMPLEADO) {
            return responderArchivo(path);
        }

        if (url.equals(usuario.getFotoUrl())) {
            return responderArchivo(path);
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    private ResponseEntity<Resource> responderArchivo(Path path) {
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
