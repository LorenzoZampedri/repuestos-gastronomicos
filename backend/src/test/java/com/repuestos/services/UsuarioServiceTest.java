package com.repuestos.services;

import com.repuestos.config.PasswordEncoderConfig;
import com.repuestos.dto.UsuarioActualizarRequest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void registrarClienteSiempreAsignaRolCliente() {
        Usuario registrado = usuarioService.registrarCliente(
                "cliente-" + System.nanoTime(), "clave-segura", "Cliente Nuevo", "CLIENTE@EXAMPLE.COM");

        assertEquals(Usuario.Rol.CLIENTE, registrado.getRol());
        assertEquals("cliente@example.com", registrado.getEmail());
        assertTrue(registrado.getActivo());
        assertTrue(passwordEncoder.matches("clave-segura", registrado.getPasswordHash()));
    }

    @Test
    void usernameDuplicadoSeDetectaIgnorandoMayusculas() {
        String username = "duplicado-" + System.nanoTime();
        usuarioService.registrarCliente(username, "clave-segura", "Uno", null);

        NegocioException error = assertThrows(NegocioException.class,
                () -> usuarioService.registrarCliente(
                        username.toUpperCase(), "clave-segura", "Dos", null));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void usuarioInactivoQuedaDeshabilitadoEnUserDetails() {
        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        assertFalse(usuarioService.loadUserByUsername(usuario.getUsername()).isEnabled());
    }

    @Test
    void noPuedeDesactivarSuPropioUsuario() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> usuarioService.cambiarActivo(usuario.getId(), false, usuario.getUsername()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertTrue(usuarioService.buscarPorId(usuario.getId()).getActivo());
    }

    @Test
    void noPuedeQuitarseElRolAdmin() {
        UsuarioActualizarRequest request = new UsuarioActualizarRequest(
                "Autorizador", null, Usuario.Rol.EMPLEADO, true);

        NegocioException error = assertThrows(NegocioException.class,
                () -> usuarioService.actualizar(usuario.getId(), request, usuario.getUsername()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(Usuario.Rol.ADMIN, usuarioService.buscarPorId(usuario.getId()).getRol());
    }

    @Test
    void noSePuedeQuedarSinAdministradorActivo() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> usuarioService.cambiarActivo(usuario.getId(), false, "otro-admin"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertTrue(usuarioService.buscarPorId(usuario.getId()).getActivo());
    }
}
