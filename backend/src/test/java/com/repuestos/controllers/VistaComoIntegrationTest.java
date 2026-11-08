package com.repuestos.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.LogActividad;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.ProductoRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.security.JwtUtil;
import com.repuestos.services.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:vista-como-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "JWT_SECRET=clave-de-test-suficientemente-larga-para-hs256-123456",
        "app.vista-como.ttl-minutos=30",
        "app.avatar-dir=target/test-avatares",
        "app.upload-dir=target/test-uploads"
})
class VistaComoIntegrationTest {

    private static final String PASSWORD = "clave-segura-123";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private LogActividadRepository logActividadRepository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.avatar-dir:./avatares}")
    private String avatarDir;

    private Usuario crearUsuario(Usuario.Rol rol, boolean activo) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        return usuarioRepository.save(Usuario.builder()
                .username(rol.name().toLowerCase() + "-" + sufijo)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .nombre("Usuario " + rol.name() + " " + sufijo)
                .email(sufijo + "@example.com")
                .rol(rol)
                .activo(activo)
                .build());
    }

    private String tokenDe(Usuario usuario) {
        return jwtUtil.generateToken(
                usuarioService.loadUserByUsername(usuario.getUsername()),
                usuario.getRol().name());
    }

    private String iniciarVista(String adminToken, Long observadoId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/usuarios/" + observadoId + "/vista-como")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soloLectura").value(true))
                .andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    private long contar(AccionAuditoria accion, Long entidadId) {
        return logActividadRepository.findAll().stream()
                .filter(log -> accion.name().equals(log.getAccion()))
                .filter(log -> entidadId == null || entidadId.equals(log.getEntidadId()))
                .count();
    }

    @Test
    void laVistaAutenticaComoElObservadoConSusPermisosReales() throws Exception {
        Usuario admin = crearUsuario(Usuario.Rol.ADMIN, true);
        Usuario empleado = crearUsuario(Usuario.Rol.EMPLEADO, true);
        Usuario cliente = crearUsuario(Usuario.Rol.CLIENTE, true);
        String adminToken = tokenDe(admin);
        String empleadoToken = iniciarVista(adminToken, empleado.getId());
        String clienteToken = iniciarVista(adminToken, cliente.getId());

        String rango = "?inicio=2024-01-01T00:00:00&fin=2030-01-01T00:00:00";
        mockMvc.perform(get("/api/ventas" + rango).header("Authorization", "Bearer " + empleadoToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + empleadoToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + empleadoToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + empleadoToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/logs").header("Authorization", "Bearer " + empleadoToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/pedidos/mios").header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ventas" + rango).header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isForbidden());

        String archivo = UUID.randomUUID() + ".jpg";
        Path directorio = Paths.get(avatarDir).toAbsolutePath().normalize();
        Files.createDirectories(directorio);
        Files.write(directorio.resolve(archivo), new byte[]{1, 2, 3});
        cliente.setFotoUrl("/api/avatares/" + archivo);
        usuarioRepository.save(cliente);

        mockMvc.perform(get("/api/avatares/" + archivo)
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk());
    }

    @Test
    void conTokenDeVistaTodasLasEscriturasSeBloqueanYFinalizarPasa() throws Exception {
        Usuario admin = crearUsuario(Usuario.Rol.ADMIN, true);
        Usuario empleado = crearUsuario(Usuario.Rol.EMPLEADO, true);
        Usuario cliente = crearUsuario(Usuario.Rol.CLIENTE, true);
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto vista " + UUID.randomUUID())
                .precio(new BigDecimal("100.00"))
                .stockActual(10)
                .stockMinimo(2)
                .activo(true)
                .build());

        String adminToken = tokenDe(admin);
        String empleadoToken = iniciarVista(adminToken, empleado.getId());
        String clienteToken = iniciarVista(adminToken, cliente.getId());

        mockMvc.perform(post("/api/ventas")
                        .header("Authorization", "Bearer " + empleadoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODO_VISTA_SOLO_LECTURA"));
        mockMvc.perform(put("/api/productos/" + producto.getId())
                        .header("Authorization", "Bearer " + empleadoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODO_VISTA_SOLO_LECTURA"));
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + empleadoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"x\",\"passwordNueva\":\"y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODO_VISTA_SOLO_LECTURA"));
        mockMvc.perform(multipart("/api/auth/foto")
                        .file(new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[]{1}))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", "Bearer " + empleadoToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODO_VISTA_SOLO_LECTURA"));
        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[],\"telefono\":\"1155551234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODO_VISTA_SOLO_LECTURA"));

        mockMvc.perform(post("/api/auth/vista-como/fin")
                        .header("Authorization", "Bearer " + empleadoToken))
                .andExpect(status().isNoContent());

        assertEquals(1, contar(AccionAuditoria.VISTA_COMO_FINALIZADA, empleado.getId()));
        LogActividad fin = logActividadRepository.findAll().stream()
                .filter(log -> AccionAuditoria.VISTA_COMO_FINALIZADA.name().equals(log.getAccion()))
                .filter(log -> empleado.getId().equals(log.getEntidadId()))
                .findFirst().orElseThrow();
        assertEquals(admin.getUsername(), fin.getUsuarioUsername());
    }

    @Test
    void iniciarValidaLasReglasYAuditaUnaSolaVez() throws Exception {
        Usuario admin = crearUsuario(Usuario.Rol.ADMIN, true);
        Usuario empleado = crearUsuario(Usuario.Rol.EMPLEADO, true);
        Usuario otroAdmin = crearUsuario(Usuario.Rol.ADMIN, true);
        Usuario inactivo = crearUsuario(Usuario.Rol.EMPLEADO, false);
        Usuario empleadoSinPermiso = crearUsuario(Usuario.Rol.EMPLEADO, true);
        String adminToken = tokenDe(admin);
        String empleadoSinPermisoToken = tokenDe(empleadoSinPermiso);

        mockMvc.perform(post("/api/usuarios/" + admin.getId() + "/vista-como")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No podés ver el sistema como vos mismo"));
        mockMvc.perform(post("/api/usuarios/" + otroAdmin.getId() + "/vista-como")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No podés ver el sistema como otro administrador"));
        mockMvc.perform(post("/api/usuarios/" + inactivo.getId() + "/vista-como")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No podés ver el sistema como un usuario desactivado"));
        mockMvc.perform(post("/api/usuarios/999999/vista-como")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/usuarios/" + empleado.getId() + "/vista-como")
                        .header("Authorization", "Bearer " + empleadoSinPermisoToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/usuarios/" + empleado.getId() + "/vista-como"))
                .andExpect(status().isUnauthorized());

        String vistaToken = iniciarVista(adminToken, empleado.getId());
        mockMvc.perform(post("/api/usuarios/" + otroAdmin.getId() + "/vista-como")
                        .header("Authorization", "Bearer " + vistaToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + vistaToken))
                .andExpect(status().isForbidden());

        assertEquals(1, contar(AccionAuditoria.VISTA_COMO_INICIADA, empleado.getId()));
        assertEquals(0, logActividadRepository.findAll().stream()
                .filter(log -> AccionAuditoria.ACCESO_DENEGADO.name().equals(log.getAccion()))
                .filter(log -> empleado.getUsername().equals(log.getUsuarioUsername()))
                .count());
        LogActividad inicio = logActividadRepository.findAll().stream()
                .filter(log -> AccionAuditoria.VISTA_COMO_INICIADA.name().equals(log.getAccion()))
                .filter(log -> empleado.getId().equals(log.getEntidadId()))
                .findFirst().orElseThrow();
        assertEquals(admin.getUsername(), inicio.getUsuarioUsername());
        assertTrue(inicio.getDetalle().contains(empleado.getUsername()));
    }

    @Test
    void finalizarConTokenNormalRespondeConflicto() throws Exception {
        Usuario admin = crearUsuario(Usuario.Rol.ADMIN, true);
        mockMvc.perform(post("/api/auth/vista-como/fin")
                        .header("Authorization", "Bearer " + tokenDe(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No estás en modo vista"));
    }

    @Test
    void siElAdminOElObservadoSeInvalidaLaVistaCorta() throws Exception {
        Usuario admin = crearUsuario(Usuario.Rol.ADMIN, true);
        Usuario empleado = crearUsuario(Usuario.Rol.EMPLEADO, true);
        String adminToken = tokenDe(admin);
        String vistaToken = iniciarVista(adminToken, empleado.getId());

        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + vistaToken))
                .andExpect(status().isOk());

        admin.setActivo(false);
        usuarioRepository.save(admin);
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + vistaToken))
                .andExpect(status().isUnauthorized());

        admin.setActivo(true);
        admin.setRol(Usuario.Rol.EMPLEADO);
        usuarioRepository.save(admin);
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + vistaToken))
                .andExpect(status().isUnauthorized());

        admin.setRol(Usuario.Rol.ADMIN);
        usuarioRepository.save(admin);
        empleado.setActivo(false);
        usuarioRepository.save(empleado);
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + vistaToken))
                .andExpect(status().isUnauthorized());

        empleado.setActivo(true);
        usuarioRepository.save(empleado);
        String vistaToken2 = iniciarVista(tokenDe(admin), empleado.getId());
        usuarioRepository.delete(empleado);
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + vistaToken2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elTokenNormalSigueFuncionandoIgual() throws Exception {
        Usuario admin = crearUsuario(Usuario.Rol.ADMIN, true);
        Usuario empleado = crearUsuario(Usuario.Rol.EMPLEADO, true);
        String adminToken = tokenDe(admin);
        String empleadoToken = tokenDe(empleado);

        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + empleadoToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + empleadoToken))
                .andExpect(status().isForbidden());
    }
}
