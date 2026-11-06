package com.repuestos.controllers;

import com.repuestos.models.Cliente;
import com.repuestos.models.Pago;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.CategoriaRepository;
import com.repuestos.security.JwtAuthFilter;
import com.repuestos.security.JwtUtil;
import com.repuestos.security.SecurityConfig;
import com.repuestos.services.ClienteService;
import com.repuestos.services.ConfiguracionService;
import com.repuestos.services.DashboardService;
import com.repuestos.services.ImagenStorageService;
import com.repuestos.services.ProductoService;
import com.repuestos.services.UsuarioService;
import com.repuestos.services.VentaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        CategoriaController.class,
        ClienteController.class,
        ConfiguracionController.class,
        DashboardController.class,
        ProductoController.class,
        UsuarioController.class,
        VentaController.class
})
@Import({SecurityConfig.class, JwtAuthFilter.class})
class SecurityRolesTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CategoriaRepository categoriaRepository;
    @MockBean private ClienteService clienteService;
    @MockBean private ConfiguracionService configuracionService;
    @MockBean private DashboardService dashboardService;
    @MockBean private ImagenStorageService imagenStorageService;
    @MockBean private ProductoService productoService;
    @MockBean private UsuarioService usuarioService;
    @MockBean private VentaService ventaService;

    @Test
    void sinTokenSoloPermiteEndpointsPublicos() throws Exception {
        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized());

        when(productoService.listarTodos()).thenReturn(List.of());
        mockMvc.perform(get("/api/productos/catalogo"))
                .andExpect(status().isOk());
    }

    @Test
    void empleadoPuedeCrearCliente() throws Exception {
        when(clienteService.guardar(any())).thenReturn(
                Cliente.builder().id(3L).nombre("Cliente").build());

        mockMvc.perform(post("/api/clientes")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("empleado").roles("EMPLEADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Cliente\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoPuedeCrearYEditarProductosPeroNoEliminarlos() throws Exception {
        Producto producto = Producto.builder().id(1L).nombre("Producto")
                .precio(new BigDecimal("100.00")).build();
        when(productoService.guardar(any())).thenReturn(producto);
        when(productoService.actualizar(eq(1L), any())).thenReturn(producto);

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Producto\",\"precio\":100}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/productos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Producto\",\"precio\":100}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/productos/1"))
                .andExpect(status().isForbidden());

        verify(productoService, never()).eliminar(any());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoPuedeCrearVenta() throws Exception {
        Usuario empleado = Usuario.builder().id(2L).username("empleado")
                .nombre("Empleado").rol(Usuario.Rol.EMPLEADO).build();
        when(usuarioService.buscarPorUsername("empleado")).thenReturn(empleado);
        when(ventaService.crearVentaConPago(any(), any(), any(), any())).thenReturn(
                Venta.builder().id(10L).usuario(empleado).fecha(LocalDateTime.now())
                        .total(new BigDecimal("100.00")).estado(Venta.EstadoVenta.PAGADA).build());

        mockMvc.perform(post("/api/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\":[],\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoNoAccedeAOperacionesDeAdministrador() throws Exception {
        mockMvc.perform(put("/api/ventas/1/anular")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/clientes/1")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/clientes/1/saldo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":10,\"tipo\":\"sumar\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clientes/deudores")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clientes/1/pagos")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ventas/pendientes")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ventas/cliente/1")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/configuracion")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteNoAccedeAlCatalogoInterno() throws Exception {
        mockMvc.perform(get("/api/productos")).andExpect(status().isForbidden());

        when(productoService.listarTodos()).thenReturn(List.of());
        mockMvc.perform(get("/api/productos/catalogo")).andExpect(status().isOk());
    }

    @Test
    void catalogoPublicoNoExponeDatosInternos() throws Exception {
        Producto producto = Producto.builder().id(1L).nombre("Producto")
                .codigoBarras("123456").stockActual(10).stockMinimo(2)
                .precio(new BigDecimal("100.00")).build();
        when(productoService.listarTodos()).thenReturn(List.of(producto));

        mockMvc.perform(get("/api/productos/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Producto"))
                .andExpect(jsonPath("$[0].codigoBarras").doesNotExist())
                .andExpect(jsonPath("$[0].stockMinimo").doesNotExist());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoNoRecibeDatosDeCuentaCorriente() throws Exception {
        when(ventaService.buscarPorId(1L)).thenReturn(ventaConCuentaCorriente());

        mockMvc.perform(get("/api/ventas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoPagado").doesNotExist())
                .andExpect(jsonPath("$.saldoPendiente").doesNotExist())
                .andExpect(jsonPath("$.cliente.saldoCorriente").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminRecibeDatosDeCuentaCorriente() throws Exception {
        when(usuarioService.esAdminActual()).thenReturn(true);
        when(ventaService.buscarPorId(1L)).thenReturn(ventaConCuentaCorriente());

        mockMvc.perform(get("/api/ventas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoPagado").value(50.00))
                .andExpect(jsonPath("$.saldoPendiente").value(50.00))
                .andExpect(jsonPath("$.cliente.saldoCorriente").value(50.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void respuestaDeUsuariosNoExponePasswordHash() throws Exception {
        Usuario usuario = Usuario.builder().id(1L).username("admin").nombre("Admin")
                .passwordHash("hash-secreto").rol(Usuario.Rol.ADMIN).activo(true).build();
        when(usuarioService.listar(null, null)).thenReturn(List.of(usuario));

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAccedeAConfiguracionDashboardYUsuarios() throws Exception {
        when(configuracionService.listarTodas()).thenReturn(List.of());
        when(dashboardService.obtenerEstadisticas()).thenReturn(Map.of());
        when(usuarioService.listar(null, null)).thenReturn(List.of());
        when(ventaService.ventasPendientes()).thenReturn(List.of());

        mockMvc.perform(get("/api/configuracion")).andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
        mockMvc.perform(get("/api/usuarios")).andExpect(status().isOk());
        mockMvc.perform(get("/api/ventas/pendientes")).andExpect(status().isOk());
    }

    @Test
    void registroPublicoIgnoraRolEnviadoYRespondeCliente() throws Exception {
        when(usuarioService.registrarCliente("nuevo", "clave-segura", "Nuevo", "nuevo@example.com"))
                .thenReturn(Usuario.builder().id(20L).username("nuevo").nombre("Nuevo")
                        .rol(Usuario.Rol.CLIENTE).activo(true).build());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo\",\"password\":\"clave-segura\","
                                + "\"nombre\":\"Nuevo\",\"email\":\"nuevo@example.com\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("CLIENTE"));

        verify(usuarioService).registrarCliente("nuevo", "clave-segura", "Nuevo", "nuevo@example.com");
    }

    @Test
    void loginDeUsuarioInactivoRespondeProhibido() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("Usuario desactivado"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"inactivo\",\"password\":\"clave-segura\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Tu usuario está desactivado. Contactá al administrador."));
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void usuarioAutenticadoPuedeCambiarSuPassword() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"clave-vieja\",\"passwordNueva\":\"clave-nueva\"}"))
                .andExpect(status().isOk());

        verify(usuarioService).cambiarPasswordActual("clave-vieja", "clave-nueva");
    }

    private Venta ventaConCuentaCorriente() {
        Cliente cliente = Cliente.builder().id(3L).nombre("Cliente")
                .saldoCorriente(new BigDecimal("50.00")).build();
        Pago pago = Pago.builder().id(5L).monto(new BigDecimal("50.00"))
                .metodoPago(Pago.MetodoPago.EFECTIVO).moneda("ARS")
                .fecha(LocalDateTime.now()).build();
        return Venta.builder().id(1L).cliente(cliente)
                .usuario(Usuario.builder().id(1L).username("admin").nombre("Admin")
                        .rol(Usuario.Rol.ADMIN).build())
                .fecha(LocalDateTime.now()).total(new BigDecimal("100.00"))
                .estado(Venta.EstadoVenta.PARCIAL).pagos(List.of(pago)).build();
    }
}
