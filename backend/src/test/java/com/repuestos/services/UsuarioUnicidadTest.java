package com.repuestos.services;

import com.repuestos.config.PasswordEncoderConfig;
import com.repuestos.dto.UsuarioActualizarRequest;
import com.repuestos.dto.UsuarioCrearRequest;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Cliente;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({UsuarioService.class, ClienteService.class, CargoCuentaCorrienteService.class,
        VentaService.class, ProductoService.class, ConfiguracionService.class,
        AuditoriaService.class, PasswordEncoderConfig.class, FotoPerfilService.class,
        AvatarStorageService.class})
class UsuarioUnicidadTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private LogActividadRepository logRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private String unico(String base) {
        return base + SEQ.incrementAndGet() + "-" + System.nanoTime();
    }

    private Usuario crearEmpleado(String username, String email) {
        return usuarioService.crear(new UsuarioCrearRequest(
                username, "clave-segura", "Nombre " + username, email, Usuario.Rol.EMPLEADO));
    }

    private NegocioException errorDe(Runnable accion) {
        return assertThrows(NegocioException.class, accion::run);
    }

    private void assertDuplicadoUsername(NegocioException error) {
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals(UsuarioUnicidad.CODE_USERNAME_DUPLICADO, error.getCode());
        assertEquals("username", error.getDatos().get("campo"));
        assertEquals(UsuarioUnicidad.MSG_USERNAME_DUPLICADO, error.getMessage());
    }

    private void assertDuplicadoEmail(NegocioException error) {
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals(UsuarioUnicidad.CODE_EMAIL_DUPLICADO, error.getCode());
        assertEquals("email", error.getDatos().get("campo"));
        assertEquals(UsuarioUnicidad.MSG_EMAIL_DUPLICADO, error.getMessage());
    }

    @Test
    void rechazaUsernameDuplicadoExactoConOtraCapitalizacionYConEspacios() {
        String username = unico("duplicado-");
        crearEmpleado(username, null);

        assertDuplicadoUsername(errorDe(() -> crearEmpleado(username, null)));
        assertDuplicadoUsername(errorDe(() -> crearEmpleado(username.toUpperCase(), null)));
        assertDuplicadoUsername(errorDe(() -> crearEmpleado("  " + username + "  ", null)));

        assertEquals(1, usuarioRepository.findAll().stream()
                .filter(usuario -> username.equalsIgnoreCase(usuario.getUsername()))
                .count());
    }

    @Test
    void rechazaUsernamesConFormatoInvalido() {
        String[] invalidos = {
                "ab",
                "a".repeat(51),
                "con espacio",
                "acentuado-á",
                "zero\u200b",
                "cirílica-а",
                "barra/x",
        };

        for (String username : invalidos) {
            NegocioException error = errorDe(() -> usuarioService.registrar(Usuario.builder()
                    .username(username)
                    .passwordHash("clave-segura")
                    .nombre("Inválido")
                    .rol(Usuario.Rol.EMPLEADO)
                    .activo(true)
                    .build()));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus(), username);
            assertNotNull(error.getMessage());
        }

        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void aceptaUsernamesConPuntoGuionYGuionBajo() {
        String base = unico("valido");
        Usuario conPunto = crearEmpleado(base + ".punto", null);
        Usuario conGuion = crearEmpleado(base + "-guion", null);
        Usuario conGuionBajo = crearEmpleado(base + "_guion_bajo", null);

        assertNotNull(conPunto.getId());
        assertNotNull(conGuion.getId());
        assertNotNull(conGuionBajo.getId());
    }

    @Test
    void rechazaEmailDuplicadoEnAltaPublicaYEnAltaDeAdmin() {
        String email = unico("correo") + "@example.com";
        crearEmpleado(unico("mail-"), email);

        assertDuplicadoEmail(errorDe(() -> usuarioService.registrarCliente(
                unico("publico-"), "clave-segura", "Público", email)));
        assertDuplicadoEmail(errorDe(() -> crearEmpleado(unico("admin-"), email.toUpperCase())));
        assertDuplicadoEmail(errorDe(() -> crearEmpleado(unico("admin-"), "  " + email + "  ")));
    }

    @Test
    void rechazaEmailDuplicadoEnEdicion() {
        String emailUno = unico("uno") + "@example.com";
        crearEmpleado(unico("edita-uno-"), emailUno);
        Usuario dos = crearEmpleado(unico("edita-dos-"), unico("dos") + "@example.com");

        NegocioException error = errorDe(() -> usuarioService.actualizar(dos.getId(),
                new UsuarioActualizarRequest("Dos", emailUno.toUpperCase(),
                        Usuario.Rol.EMPLEADO, true),
                "admin"));
        assertDuplicadoEmail(error);
    }

    @Test
    void permiteGuardarElPropioEmailAlEditar() {
        String email = unico("propio") + "@example.com";
        Usuario usuario = crearEmpleado(unico("edita-propio-"), email);

        Usuario mismo = usuarioService.actualizar(usuario.getId(),
                new UsuarioActualizarRequest("Nombre editado", "  " + email + "  ",
                        Usuario.Rol.EMPLEADO, true),
                "admin");

        assertEquals(email, mismo.getEmail());
        assertEquals(usuario.getUsername(), mismo.getUsername());
    }

    @Test
    void permiteVariosUsuariosSinEmail() {
        Usuario sinEmail = crearEmpleado(unico("sin-email-"), null);
        Usuario vacio = crearEmpleado(unico("email-vacio-"), "   ");
        Usuario otroSinEmail = usuarioService.registrarCliente(
                unico("publico-sin-email-"), "clave-segura", "Público", null);

        assertNull(sinEmail.getEmail());
        assertNull(vacio.getEmail());
        assertNull(otroSinEmail.getEmail());
    }

    @Test
    void mismoEmailEntreUsuarioYClienteNoEsConflicto() {
        String email = unico("compartido") + "@example.com";
        clienteRepository.save(Cliente.builder()
                .nombre("Cliente Compartido")
                .email(email)
                .saldoCorriente(BigDecimal.ZERO)
                .activo(true)
                .build());

        Usuario usuario = crearEmpleado(unico("usuario-"), email);

        assertEquals(email, usuario.getEmail());
    }

    @Test
    void permitePasswordsYNombreParaMostrarRepetidos() {
        String nombre = "Homónimo " + SEQ.incrementAndGet();
        Usuario uno = usuarioService.registrar(Usuario.builder()
                .username(unico("clave-uno-"))
                .passwordHash("misma-clave")
                .nombre(nombre)
                .rol(Usuario.Rol.EMPLEADO)
                .activo(true)
                .build());
        Usuario dos = usuarioService.registrar(Usuario.builder()
                .username(unico("clave-dos-"))
                .passwordHash("misma-clave")
                .nombre(nombre)
                .rol(Usuario.Rol.EMPLEADO)
                .activo(true)
                .build());

        assertEquals(nombre, uno.getNombre());
        assertEquals(nombre, dos.getNombre());
        assertTrue(passwordEncoder.matches("misma-clave", uno.getPasswordHash()));
        assertTrue(passwordEncoder.matches("misma-clave", dos.getPasswordHash()));
    }

    @Test
    void unUsuarioLegacyConUsernameFueraDelPatronSigueIniciandoSesion() {
        Usuario legacy = usuarioRepository.save(Usuario.builder()
                .username("usér.legacy-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("clave-segura"))
                .nombre("Legacy")
                .rol(Usuario.Rol.EMPLEADO)
                .activo(true)
                .build());

        assertTrue(usuarioService.loadUserByUsername(legacy.getUsername()).isEnabled());
        assertEquals(legacy.getUsername(),
                usuarioService.buscarPorUsername(legacy.getUsername()).getUsername());
    }

    @Test
    void unDuplicadoRechazadoNoGeneraAuditoria() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("actor-admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        String username = unico("auditado-");
        crearEmpleado(username, null);
        long logsAntes = logRepository.findAll().stream()
                .filter(log -> AccionAuditoria.USUARIO_CREADO.name().equals(log.getAccion()))
                .count();

        assertDuplicadoUsername(errorDe(() -> crearEmpleado(username, null)));

        long logsDespues = logRepository.findAll().stream()
                .filter(log -> AccionAuditoria.USUARIO_CREADO.name().equals(log.getAccion()))
                .count();
        assertEquals(logsAntes, logsDespues);
        assertEquals(1, usuarioRepository.findAll().stream()
                .filter(usuario -> username.equalsIgnoreCase(usuario.getUsername()))
                .count());
    }
}
