package com.repuestos.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.LogActividad;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(AuditoriaService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditoriaServiceTest {

    @Autowired private AuditoriaService auditoriaService;
    @Autowired private LogActividadRepository logActividadRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Usuario admin;
    private Usuario cliente;

    @BeforeEach
    void setUp() {
        logActividadRepository.deleteAll();
        auditoriaService.limpiarThrottle();
        SecurityContextHolder.clearContext();
        admin = usuarioRepository.save(Usuario.builder()
                .username("audit-admin-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Admin Auditoría")
                .rol(Usuario.Rol.ADMIN)
                .activo(true)
                .build());
        cliente = usuarioRepository.save(Usuario.builder()
                .username("audit-cliente-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Cliente Auditoría")
                .rol(Usuario.Rol.CLIENTE)
                .activo(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        auditoriaService.limpiarThrottle();
    }

    private void autenticar(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))));
    }

    @Test
    void registraActorRolYRedactaDatosSensibles() throws Exception {
        autenticar(admin);

        Map<String, Object> anidado = new LinkedHashMap<>();
        anidado.put("authorization", "Bearer secreto");
        anidado.put("nombre", "Producto");
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("password", "clave-plana");
        detalle.put("token", "jwt-secreto");
        detalle.put("cuit", "20123456789");
        detalle.put("anidado", anidado);

        auditoriaService.registrar(AccionAuditoria.PRODUCTO_CREADO, "Producto", 15L,
                "Creó el producto 'Producto'", detalle);

        List<LogActividad> logs = logActividadRepository.findAll();
        assertEquals(1, logs.size());
        LogActividad log = logs.get(0);
        assertEquals(admin.getId(), log.getUsuarioId());
        assertEquals(admin.getUsername(), log.getUsuarioUsername());
        assertEquals("ADMIN", log.getUsuarioRol());
        assertEquals("PRODUCTO_CREADO", log.getAccion());
        assertEquals("PRODUCTOS", log.getModulo());
        assertEquals("Producto", log.getEntidadTipo());
        assertEquals(15L, log.getEntidadId());
        assertEquals("OK", log.getResultado());
        assertNotNull(log.getFecha());

        JsonNode json = new ObjectMapper().readTree(log.getDetalle());
        assertEquals("[REDACTADO]", json.get("password").asText());
        assertEquals("[REDACTADO]", json.get("token").asText());
        assertEquals("20123456789", json.get("cuit").asText());
        assertEquals("[REDACTADO]", json.get("anidado").get("authorization").asText());
        assertEquals("Producto", json.get("anidado").get("nombre").asText());
    }

    @Test
    void sinActorNoRegistraNada() {
        auditoriaService.registrar(AccionAuditoria.PRODUCTO_CREADO, "Producto", 1L,
                "Sin sesión", Map.of());

        assertEquals(0, logActividadRepository.count());
    }

    @Test
    void clienteSoloPuedeGenerarAccionesDePedido() {
        autenticar(cliente);

        auditoriaService.registrar(AccionAuditoria.PRODUCTO_ELIMINADO, "Producto", 1L,
                "Intento de eliminar", Map.of());
        assertEquals(0, logActividadRepository.count());

        auditoriaService.registrar(AccionAuditoria.PEDIDO_CREADO, "Pedido", 2L,
                "Creó el pedido #2", Map.of());
        assertEquals(1, logActividadRepository.count());
    }

    @Test
    void loginFallidoSeRegistraSinSesionPeroNoParaClientes() {
        auditoriaService.registrarLoginFallido(admin.getUsername(), "credenciales inválidas");

        List<LogActividad> logs = logActividadRepository.findAll();
        assertEquals(1, logs.size());
        LogActividad log = logs.get(0);
        assertEquals("LOGIN_FALLIDO", log.getAccion());
        assertEquals("AUTH", log.getModulo());
        assertEquals("FALLIDO", log.getResultado());
        assertEquals(admin.getUsername(), log.getUsuarioUsername());
        assertEquals(admin.getId(), log.getUsuarioId());

        auditoriaService.registrarLoginFallido(cliente.getUsername(), "credenciales inválidas");
        assertEquals(1, logActividadRepository.count());

        auditoriaService.registrarLoginFallido("inexistente-" + System.nanoTime(),
                "credenciales inválidas");
        assertEquals(2, logActividadRepository.count());
    }

    @Test
    void accesoDenegadoSeRegistraUnaVezPorMinutoYPorRuta() {
        autenticar(admin);

        auditoriaService.registrarAccesoDenegado("GET", "/api/logs");
        auditoriaService.registrarAccesoDenegado("GET", "/api/logs");

        List<LogActividad> logs = logActividadRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("ACCESO_DENEGADO", logs.get(0).getAccion());
        assertEquals("SEGURIDAD", logs.get(0).getModulo());
        assertEquals("FALLIDO", logs.get(0).getResultado());

        auditoriaService.registrarAccesoDenegado("GET", "/api/usuarios");
        assertEquals(2, logActividadRepository.count());
    }

    @Test
    void clienteNoGeneraAccesoDenegado() {
        autenticar(cliente);

        auditoriaService.registrarAccesoDenegado("GET", "/api/logs");

        assertEquals(0, logActividadRepository.count());
    }

    @Test
    void truncaResumenYDetalle() throws Exception {
        autenticar(admin);
        String resumen = "x".repeat(800);
        String texto = "y".repeat(30000);

        auditoriaService.registrar(AccionAuditoria.CONFIG_EDITADA, "Configuracion", null,
                resumen, Map.of("valor", texto));

        LogActividad log = logActividadRepository.findAll().get(0);
        assertEquals(500, log.getResumen().length());
        JsonNode json = new ObjectMapper().readTree(log.getDetalle());
        assertTrue(json.get("truncado").asBoolean());
    }

    @Test
    void calcularDiffSoloDevuelveCamposModificados() {
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("nombre", "A");
        antes.put("precio", new BigDecimal("100.00"));
        antes.put("stock", 5);
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("nombre", "B");
        despues.put("precio", new BigDecimal("100.0"));
        despues.put("stock", 5);

        Map<String, Object> diff = auditoriaService.calcularDiff(antes, despues);

        assertEquals(1, diff.size());
        assertTrue(diff.containsKey("nombre"));
        assertFalse(diff.containsKey("precio"));
        assertFalse(diff.containsKey("stock"));
    }

    @Test
    void noRompeElNegocioSiElDetalleNoEsSerializable() {
        autenticar(admin);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("fecha", LocalDateTime.now());

        auditoriaService.registrar(AccionAuditoria.CONFIG_EDITADA, "Configuracion", null,
                "Editó configuración", detalle);

        assertEquals(1, logActividadRepository.count());
    }
}
