package com.repuestos.services;

import com.repuestos.config.PasswordEncoderConfig;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({UsuarioService.class, PasswordEncoderConfig.class})
class UsuarioServiceTest {

    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("autorizador-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("clave-segura"))
                .nombre("Autorizador")
                .rol(Usuario.Rol.ADMIN)
                .build());
    }

    @Test
    void verificarPasswordCorrectaDevuelveUsuario() {
        Usuario verificado = usuarioService.verificarPassword(usuario.getUsername(), "clave-segura");

        assertEquals(usuario.getId(), verificado.getId());
    }

    @Test
    void verificarPasswordIncorrectaRechazaLaOperacion() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> usuarioService.verificarPassword(usuario.getUsername(), "incorrecta"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void verificarPasswordVaciaRechazaLaOperacion() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> usuarioService.verificarPassword(usuario.getUsername(), " "));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }
}
