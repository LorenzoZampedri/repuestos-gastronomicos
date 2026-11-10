package com.repuestos.services;

import com.repuestos.config.PasswordEncoderConfig;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({UsuarioService.class, AuditoriaService.class, PasswordEncoderConfig.class})
class UsuarioAtomicidadTest {

    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private LogActividadRepository logRepository;

    @MockBean private ClienteService clienteService;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("actor-admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void siFallaLaFichaDeClienteNoQuedaUsuarioNiAuditoria() {
        autenticarAdmin();
        when(clienteService.asegurarClienteParaUsuario(any()))
                .thenThrow(new RuntimeException("fallo simulado al crear el cliente"));
        String username = "atomico-" + System.nanoTime();

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> usuarioService.registrarCliente(
                        username, "clave-segura", "Cliente Atómico", null));

        assertEquals("fallo simulado al crear el cliente", error.getMessage());
        assertTrue(usuarioRepository.findByUsernameIgnoreCase(username).isEmpty());
        assertEquals(0, clienteRepository.count());
        assertEquals(0, logRepository.findAll().stream()
                .filter(log -> AccionAuditoria.USUARIO_CREADO.name().equals(log.getAccion()))
                .count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unDuplicadoRechazadoNoDejaCuentaNiFilaDeAuditoria() {
        autenticarAdmin();
        String username = "duplicado-atomico-" + System.nanoTime();
        usuarioService.registrarCliente(username, "clave-segura", "Uno", null);
        long logsAntes = logRepository.findAll().stream()
                .filter(log -> AccionAuditoria.USUARIO_CREADO.name().equals(log.getAccion()))
                .count();

        try {
            NegocioException error = assertThrows(NegocioException.class,
                    () -> usuarioService.registrarCliente(
                            username.toUpperCase(), "clave-segura", "Dos", null));

            assertEquals(UsuarioUnicidad.CODE_USERNAME_DUPLICADO, error.getCode());
            assertEquals(1, usuarioRepository.findAll().stream()
                    .filter(usuario -> username.equalsIgnoreCase(usuario.getUsername()))
                    .count());
            assertEquals(logsAntes, logRepository.findAll().stream()
                    .filter(log -> AccionAuditoria.USUARIO_CREADO.name().equals(log.getAccion()))
                    .count());
        } finally {
            usuarioRepository.findByUsernameIgnoreCase(username)
                    .ifPresent(usuarioRepository::delete);
        }
    }
}
