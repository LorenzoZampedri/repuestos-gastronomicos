package com.repuestos.controllers;

import com.repuestos.dto.DatosTransferenciaResponse;
import com.repuestos.dto.PaginaLogsResponse;
import com.repuestos.dto.PaginaLotesResponse;
import com.repuestos.dto.PreciosEstadoResponse;
import com.repuestos.dto.PreciosVistaPreviaResponse;
import com.repuestos.dto.VistaComoResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Cliente;
import com.repuestos.models.Pago;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.CategoriaRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.security.JwtAuthFilter;
import com.repuestos.security.JwtUtil;
import com.repuestos.security.SecurityConfig;
import com.repuestos.services.AuditoriaService;
import com.repuestos.services.AvatarStorageService;
import com.repuestos.services.ClienteService;
import com.repuestos.services.ConfiguracionService;
import com.repuestos.services.CuentaCorrienteService;
import com.repuestos.services.DashboardService;
import com.repuestos.services.FotoPerfilService;
import com.repuestos.services.ImagenStorageService;
import com.repuestos.services.LogsService;
import com.repuestos.services.PedidoService;
import com.repuestos.services.PreciosMasivosService;
import com.repuestos.services.ProductoService;
import com.repuestos.services.UsuarioService;
import com.repuestos.services.VentaService;
import com.repuestos.services.VistaComoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        AvatarController.class,
        CategoriaController.class,
        ClienteController.class,
        ConfiguracionController.class,
        PedidoController.class,
        DashboardController.class,
        LogsController.class,
        PreciosController.class,
        ProductoController.class,
        UsuarioController.class,
        VentaController.class
})
@Import({SecurityConfig.class, JwtAuthFilter.class})
class SecurityRolesTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private AuditoriaService auditoriaService;
    @MockBean private AvatarStorageService avatarStorageService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CategoriaRepository categoriaRepository;
    @MockBean private ClienteService clienteService;
    @MockBean private CuentaCorrienteService cuentaCorrienteService;
    @MockBean private ConfiguracionService configuracionService;
    @MockBean private DashboardService dashboardService;
    @MockBean private FotoPerfilService fotoPerfilService;
    @MockBean private ImagenStorageService imagenStorageService;
    @MockBean private LogsService logsService;
    @MockBean private PedidoService pedidoService;
    @MockBean private PreciosMasivosService preciosMasivosService;
    @MockBean private ProductoService productoService;
    @MockBean private UsuarioRepository usuarioRepository;
    @MockBean private UsuarioService usuarioService;
    @MockBean private VentaService ventaService;
    @MockBean private VistaComoService vistaComoService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPuedeIniciarVistaComo() throws Exception {
        when(vistaComoService.iniciar(2L)).thenReturn(new VistaComoResponse(
                "token-vista", "empleado", "Empleado", "EMPLEADO", "",
                Instant.now().plusSeconds(1800), true));

        mockMvc.perform(post("/api/usuarios/2/vista-como"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-vista"))
                .andExpect(jsonPath("$.soloLectura").value(true));

        verify(vistaComoService).iniciar(2L);
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoNoPuedeIniciarVistaComo() throws Exception {
        mockMvc.perform(post("/api/usuarios/2/vista-como"))
                .andExpect(status().isForbidden());

        verify(vistaComoService, never()).iniciar(any());
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteNoPuedeIniciarVistaComo() throws Exception {
        mockMvc.perform(post("/api/usuarios/2/vista-como"))
                .andExpect(status().isForbidden());

        verify(vistaComoService, never()).iniciar(any());
    }

    @Test
    void sinTokenNoSePuedeFinalizarVistaComo() throws Exception {
        mockMvc.perform(post("/api/auth/vista-como/fin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void autenticadoPuedeFinalizarVistaComo() throws Exception {
        mockMvc.perform(post("/api/auth/vista-como/fin"))
                .andExpect(status().isNoContent());

        verify(vistaComoService).finalizar();
    }

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
    void empleadoNoPuedeVenderACuentaCorriente() throws Exception {
        Usuario empleado = Usuario.builder().id(2L).username("empleado")
                .nombre("Empleado").rol(Usuario.Rol.EMPLEADO).build();
        when(usuarioService.buscarPorUsername("empleado")).thenReturn(empleado);
        when(usuarioService.esAdminActual()).thenReturn(false);

        mockMvc.perform(post("/api/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\":[],\"metodoPago\":\"CUENTA_CORRIENTE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Solo un administrador puede vender a cuenta corriente"));

        verify(ventaService, never()).crearVentaConPago(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPuedeVenderACuentaCorriente() throws Exception {
        Usuario admin = Usuario.builder().id(1L).username("admin")
                .nombre("Admin").rol(Usuario.Rol.ADMIN).build();
        when(usuarioService.buscarPorUsername("admin")).thenReturn(admin);
        when(usuarioService.esAdminActual()).thenReturn(true);
        when(ventaService.crearVentaConPago(any(), any(), any(), any())).thenReturn(
                Venta.builder().id(11L).usuario(admin).fecha(LocalDateTime.now())
                        .total(new BigDecimal("100.00")).estado(Venta.EstadoVenta.PENDIENTE).build());

        mockMvc.perform(post("/api/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1,\"detalles\":[],"
                                + "\"metodoPago\":\"CUENTA_CORRIENTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11));
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
        mockMvc.perform(get("/api/logs")).andExpect(status().isForbidden());

        verify(auditoriaService, atLeastOnce())
                .registrarAccesoDenegado(anyString(), anyString());
    }

    @Test
    void sinTokenNoSePuedeCargarDeudaNiConsultarCargosOCompras() throws Exception {
        mockMvc.perform(post("/api/clientes/cargar-deuda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1,\"monto\":10,\"motivo\":\"Compra\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clientes/1/cargos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clientes/1/compras")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoNoPuedeCargarDeudaNiConsultarCargosPeroSiCompras() throws Exception {
        mockMvc.perform(post("/api/clientes/cargar-deuda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1,\"monto\":10,\"motivo\":\"Compra\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clientes/1/cargos")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clientes/1/compras")).andExpect(status().isOk());

        verify(cuentaCorrienteService, never()).cargarDeuda(any());
        verify(cuentaCorrienteService, never()).cargosDeCliente(any());
        verify(cuentaCorrienteService).comprasDeCliente(eq(1L), eq(20), eq(false));
        verify(auditoriaService, atLeastOnce())
                .registrarAccesoDenegado(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteNoPuedeUsarEndpointsDeCuentaCorriente() throws Exception {
        mockMvc.perform(post("/api/clientes/cargar-deuda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1,\"monto\":10,\"motivo\":\"Compra\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clientes/1/cargos")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clientes/1/compras")).andExpect(status().isForbidden());
        verify(auditoriaService, atLeastOnce())
                .registrarAccesoDenegado(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPuedeCargarDeudaConsultarCargosYCompras() throws Exception {
        when(cuentaCorrienteService.cargarDeuda(any())).thenReturn(null);
        when(usuarioService.esAdminActual()).thenReturn(true);

        mockMvc.perform(post("/api/clientes/cargar-deuda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1,\"monto\":10,\"motivo\":\"Compra\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/clientes/1/cargos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/clientes/1/compras?limite=50"))
                .andExpect(status().isOk());

        verify(cuentaCorrienteService).cargarDeuda(any());
        verify(cuentaCorrienteService).cargosDeCliente(1L);
        verify(cuentaCorrienteService).comprasDeCliente(1L, 50, true);
        mockMvc.perform(delete("/api/clientes/1")).andExpect(status().isOk());
        verify(clienteService).eliminar(1L);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAccedeALogsYEmpleadoNo() throws Exception {
        when(logsService.buscar(any())).thenReturn(
                new PaginaLogsResponse(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
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
                .andExpect(jsonPath("$.pagos").doesNotExist())
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
                .andExpect(jsonPath("$.pagos[0].monto").value(50.00))
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
    void configuracionPublicaIncluyeDatosDeTransferenciaSinToken() throws Exception {
        when(configuracionService.getCotizacionUsdArs()).thenReturn(new BigDecimal("1000"));
        when(configuracionService.obtenerValor(ConfiguracionService.CLAVE_WHATSAPP_NUMBER))
                .thenReturn("5491112345678");
        when(configuracionService.obtenerDatosTransferencia()).thenReturn(
                new DatosTransferenciaResponse("0000003100098765432101",
                        "gastorepuestos.mp", "Repuestos Gastronomicos Once"));

        mockMvc.perform(get("/api/configuracion/publica"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datosTransferencia.cvu")
                        .value("0000003100098765432101"))
                .andExpect(jsonPath("$.datosTransferencia.alias")
                        .value("gastorepuestos.mp"))
                .andExpect(jsonPath("$.datosTransferencia.titular")
                        .value("Repuestos Gastronomicos Once"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPuedeEditarDatosDeTransferencia() throws Exception {
        when(configuracionService.guardarDatosTransferencia(any())).thenReturn(
                new DatosTransferenciaResponse("1111111111111111111111",
                        "nuevo.alias", "Nuevo Titular"));

        mockMvc.perform(put("/api/configuracion/transferencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cvu\":\"1111111111111111111111\","
                                + "\"alias\":\"nuevo.alias\",\"titular\":\"Nuevo Titular\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("nuevo.alias"));
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoNoPuedeEditarDatosDeTransferencia() throws Exception {
        mockMvc.perform(put("/api/configuracion/transferencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cvu\":\"1111111111111111111111\","
                                + "\"alias\":\"nuevo.alias\",\"titular\":\"Nuevo Titular\"}"))
                .andExpect(status().isForbidden());

        verify(configuracionService, never()).guardarDatosTransferencia(any());
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteNoPuedeEditarDatosDeTransferencia() throws Exception {
        mockMvc.perform(put("/api/configuracion/transferencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cvu\":\"1111111111111111111111\","
                                + "\"alias\":\"nuevo.alias\",\"titular\":\"Nuevo Titular\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenNoSePuedenEditarDatosDeTransferencia() throws Exception {
        mockMvc.perform(put("/api/configuracion/transferencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cvu\":\"1111111111111111111111\","
                                + "\"alias\":\"nuevo.alias\",\"titular\":\"Nuevo Titular\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void elEndpointGenericoNoPermiteEditarClavesDeTransferencia() throws Exception {
        when(configuracionService.esClaveTransferencia(
                ConfiguracionService.CLAVE_TRANSFERENCIA_CVU)).thenReturn(true);

        mockMvc.perform(put("/api/configuracion/transferencia_cvu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"1111111111111111111111\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Los datos de transferencia se editan desde el formulario "
                                + "de datos de transferencia"));

        verify(configuracionService, never()).guardarOActualizar(any(), any(), any());
    }

    @Test
    void sinTokenNoSePuedenUsarPedidos() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":1}],"
                                + "\"telefono\":\"1155551234\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pedidos/mios")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pedidos/mios/contacto")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/pedidos/1/convertir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clientePuedeCrearYConsultarSusPedidosPeroNoGestionarlos() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":1}],"
                                + "\"telefono\":\"1155551234\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos/mios")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos/mios/contacto")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos/mios/1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pedidos/1/convertir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pedidos/1/rechazar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Sin stock\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoGestionaPedidosPeroNoCreaPedidosDeCliente() throws Exception {
        when(usuarioService.usuarioActual()).thenReturn(Usuario.builder()
                .id(2L).username("empleado").nombre("Empleado")
                .rol(Usuario.Rol.EMPLEADO).build());
        when(pedidoService.listarStaff(any(), eq(false))).thenReturn(List.of());

        mockMvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos/pendientes/count")).andExpect(status().isOk());
        mockMvc.perform(post("/api/pedidos/1/convertir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pedidos/1/rechazar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Sin stock\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":1}],"
                                + "\"telefono\":\"1155551234\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pedidos/mios")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pedidos/mios/contacto")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/pedidos/mios/1/cancelar")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminListaYConviertePedidosPeroNoCreaPedidosDeCliente() throws Exception {
        when(usuarioService.usuarioActual()).thenReturn(Usuario.builder()
                .id(1L).username("admin").nombre("Admin")
                .rol(Usuario.Rol.ADMIN).build());
        when(usuarioService.esAdminActual()).thenReturn(true);
        when(pedidoService.listarStaff(any(), eq(true))).thenReturn(List.of());
        when(pedidoService.contarPendientes()).thenReturn(3L);

        mockMvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos/pendientes/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
        mockMvc.perform(post("/api/pedidos/1/convertir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodoPago\":\"CUENTA_CORRIENTE\","
                                + "\"items\":[{\"detalleId\":1,\"cantidad\":99}]}"))
                .andExpect(status().isOk());

        verify(pedidoService).convertir(eq(1L), any(Usuario.class), eq(true));

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":1}],"
                                + "\"telefono\":\"1155551234\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pedidos/mios")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPuedeVincularUnClienteAUnUsuario() throws Exception {
        when(usuarioService.vincularCliente(5L, 9L)).thenReturn(Usuario.builder()
                .id(5L).username("cliente").nombre("Cliente")
                .rol(Usuario.Rol.CLIENTE).activo(true).build());

        mockMvc.perform(put("/api/usuarios/5/cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        verify(usuarioService).vincularCliente(5L, 9L);
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteNoPuedeVincularClientes() throws Exception {
        mockMvc.perform(put("/api/usuarios/5/cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":9}"))
                .andExpect(status().isForbidden());
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

        verify(auditoriaService).registrarLoginFallido("inactivo", "usuario inactivo");
    }

    @Test
    void loginCorrectoGeneraLogDeInicioDeSesion() throws Exception {
        Usuario admin = Usuario.builder().id(1L).username("admin")
                .nombre("Admin").rol(Usuario.Rol.ADMIN).build();
        when(usuarioService.loadUserByUsername("admin")).thenReturn(
                new org.springframework.security.core.userdetails.User(
                        "admin", "hash", List.of()));
        when(usuarioService.buscarPorUsername("admin")).thenReturn(admin);
        when(jwtUtil.generateToken(any(), eq("ADMIN"))).thenReturn("token-jwt");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"clave-segura\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-jwt"));

        verify(auditoriaService).registrarComo(eq(AccionAuditoria.LOGIN_OK),
                eq(admin), eq("Usuario"), eq(1L), anyString(), any(Map.class));
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

    // ------------------------------------------------------------------
    // Fotos de perfil y avatares
    // ------------------------------------------------------------------

    private static final String ARCHIVO_AVATAR = "11111111-1111-1111-1111-111111111111.jpg";

    @TempDir
    Path tempDir;

    private MockMultipartFile foto() {
        return new MockMultipartFile("file", "foto.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    @Test
    void sinTokenNoSePuedeAccederAFotos() throws Exception {
        mockMvc.perform(put("/api/auth/foto").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/auth/foto")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/usuarios/1/foto").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/usuarios/1/foto")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/clientes/1/foto").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/clientes/1/foto")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/avatares/" + ARCHIVO_AVATAR)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void usuarioAutenticadoPuedeCambiarSuPropiaFoto() throws Exception {
        Usuario empleado = Usuario.builder().id(2L).username("empleado")
                .nombre("Empleado").rol(Usuario.Rol.EMPLEADO).build();
        when(usuarioService.usuarioActual()).thenReturn(empleado);
        when(fotoPerfilService.cambiarFotoUsuario(eq(2L), any())).thenReturn(empleado);

        mockMvc.perform(multipart("/api/auth/foto").file(foto()).with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/auth/foto")).andExpect(status().isOk());

        verify(fotoPerfilService).cambiarFotoUsuario(eq(2L), any());
        verify(fotoPerfilService).quitarFotoUsuario(2L);
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoPuedeCambiarFotoDeClientePeroNoEliminarClientes() throws Exception {
        when(fotoPerfilService.quitarFotoCliente(1L)).thenReturn(
                Cliente.builder().id(1L).nombre("Cliente").build());

        mockMvc.perform(delete("/api/clientes/1/foto")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/clientes/1")).andExpect(status().isForbidden());

        verify(fotoPerfilService).quitarFotoCliente(1L);
        verify(clienteService, never()).eliminar(any());
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteNoPuedeCambiarFotosDeClientesNiDeOtrosUsuarios() throws Exception {
        mockMvc.perform(delete("/api/clientes/1/foto")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/usuarios/1/foto")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/clientes/1/foto").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/usuarios/1/foto").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());

        verify(fotoPerfilService, never()).quitarFotoCliente(any());
        verify(fotoPerfilService, never()).quitarFotoUsuario(any());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoNoPuedeCambiarFotoDeOtroUsuario() throws Exception {
        mockMvc.perform(delete("/api/usuarios/1/foto")).andExpect(status().isForbidden());
        verify(fotoPerfilService, never()).quitarFotoUsuario(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPuedeCambiarFotoDeCualquierUsuario() throws Exception {
        when(fotoPerfilService.quitarFotoUsuario(1L)).thenReturn(
                Usuario.builder().id(1L).username("objetivo").nombre("Objetivo")
                        .rol(Usuario.Rol.EMPLEADO).build());

        mockMvc.perform(delete("/api/usuarios/1/foto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPuedeVerCualquierAvatar() throws Exception {
        prepararAvatar();
        when(usuarioRepository.findByUsernameIgnoreCase("admin")).thenReturn(java.util.Optional.of(
                Usuario.builder().id(1L).username("admin").nombre("Admin")
                        .rol(Usuario.Rol.ADMIN).build()));

        mockMvc.perform(get("/api/avatares/" + ARCHIVO_AVATAR))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoPuedeVerCualquierAvatar() throws Exception {
        prepararAvatar();
        when(usuarioRepository.findByUsernameIgnoreCase("empleado")).thenReturn(java.util.Optional.of(
                Usuario.builder().id(2L).username("empleado").nombre("Empleado")
                        .rol(Usuario.Rol.EMPLEADO).build()));

        mockMvc.perform(get("/api/avatares/" + ARCHIVO_AVATAR))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteSoloVeSuPropioAvatar() throws Exception {
        prepararAvatar();
        String propio = "/api/avatares/" + ARCHIVO_AVATAR;
        when(usuarioRepository.findByUsernameIgnoreCase("cliente")).thenReturn(java.util.Optional.of(
                Usuario.builder().id(3L).username("cliente").nombre("Cliente")
                        .rol(Usuario.Rol.CLIENTE).fotoUrl(propio).build()));

        mockMvc.perform(get(propio)).andExpect(status().isOk());

        String ajeno = "/api/avatares/22222222-2222-2222-2222-222222222222.jpg";
        when(avatarStorageService.cargar("22222222-2222-2222-2222-222222222222.jpg"))
                .thenReturn(tempDir.resolve(ARCHIVO_AVATAR));
        mockMvc.perform(get(ajeno)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void nombreDeAvatarInvalidoRespondeNotFound() throws Exception {
        when(usuarioRepository.findByUsernameIgnoreCase("admin")).thenReturn(java.util.Optional.of(
                Usuario.builder().id(1L).username("admin").nombre("Admin")
                        .rol(Usuario.Rol.ADMIN).build()));

        mockMvc.perform(get("/api/avatares/no-es-un-uuid.jpg"))
                .andExpect(status().isNotFound());
    }

    private void prepararAvatar() throws Exception {
        Path archivo = tempDir.resolve(ARCHIVO_AVATAR);
        Files.write(archivo, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        when(avatarStorageService.cargar(ARCHIVO_AVATAR)).thenReturn(archivo);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void respuestaDeUsuariosIncluyeFotoUrl() throws Exception {
        Usuario usuario = Usuario.builder().id(1L).username("admin").nombre("Admin")
                .rol(Usuario.Rol.ADMIN).activo(true)
                .fotoUrl("/api/avatares/" + ARCHIVO_AVATAR).build();
        when(usuarioService.listar(null, null)).thenReturn(List.of(usuario));

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fotoUrl").value("/api/avatares/" + ARCHIVO_AVATAR));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listadoDeClientesIncluyeFotoYCuentaVinculadaSinConsultasPorCliente() throws Exception {
        when(usuarioService.esAdminActual()).thenReturn(true);
        when(clienteService.listarTodos()).thenReturn(List.of(
                Cliente.builder().id(1L).nombre("Con Cuenta")
                        .fotoUrl("/api/avatares/" + ARCHIVO_AVATAR).build(),
                Cliente.builder().id(2L).nombre("Sin Cuenta").build()));
        when(usuarioRepository.findClienteIdsConCuenta()).thenReturn(List.of(1L));

        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fotoUrl").value("/api/avatares/" + ARCHIVO_AVATAR))
                .andExpect(jsonPath("$[0].cuentaVinculada").value(true))
                .andExpect(jsonPath("$[1].cuentaVinculada").value(false));

        verify(usuarioRepository, times(1)).findClienteIdsConCuenta();
    }

    @Test
    void lasFotosDePersonasNoSeSirvenDesdeUploads() throws Exception {
        mockMvc.perform(get("/api/uploads/avatares/" + ARCHIVO_AVATAR))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Precios masivos (solo ADMIN)
    // ------------------------------------------------------------------

    @Test
    void sinTokenNoSePuedenUsarPrecios() throws Exception {
        mockMvc.perform(get("/api/precios/estado")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/precios/lotes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/precios/lotes/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/precios/vista-previa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PORCENTAJE\",\"valor\":10,"
                                + "\"alcance\":{\"tipo\":\"TODOS\"}}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/precios/aplicar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PORCENTAJE\",\"valor\":10,"
                                + "\"alcance\":{\"tipo\":\"TODOS\"},\"token\":\"abc\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/precios/lotes/1/revertir"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/precios/recalcular-ars"))
                .andExpect(status().isUnauthorized());

        verify(preciosMasivosService, never()).estado();
        verify(preciosMasivosService, never()).vistaPrevia(any());
        verify(preciosMasivosService, never()).aplicar(any());
    }

    @Test
    @WithMockUser(username = "empleado", roles = "EMPLEADO")
    void empleadoNoAccedeAPrecios() throws Exception {
        mockMvc.perform(get("/api/precios/estado")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/precios/lotes")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/precios/vista-previa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PORCENTAJE\",\"valor\":10,"
                                + "\"alcance\":{\"tipo\":\"TODOS\"}}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/precios/aplicar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PORCENTAJE\",\"valor\":10,"
                                + "\"alcance\":{\"tipo\":\"TODOS\"},\"token\":\"abc\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/precios/lotes/1/revertir"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/precios/recalcular-ars"))
                .andExpect(status().isForbidden());

        verify(preciosMasivosService, never()).estado();
        verify(preciosMasivosService, never()).aplicar(any());
        verify(auditoriaService, atLeastOnce())
                .registrarAccesoDenegado(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteNoAccedeAPrecios() throws Exception {
        mockMvc.perform(get("/api/precios/estado")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/precios/lotes")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/precios/recalcular-ars"))
                .andExpect(status().isForbidden());

        verify(preciosMasivosService, never()).estado();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAccedeALaVistaPreviaEstadoYHistorialDePrecios() throws Exception {
        when(preciosMasivosService.estado()).thenReturn(
                new PreciosEstadoResponse(new BigDecimal("1000"), 2, 1));
        when(preciosMasivosService.listarLotes(0, 20)).thenReturn(
                new PaginaLotesResponse(List.of(), 0, 0, 0, 20));
        when(preciosMasivosService.vistaPrevia(any())).thenReturn(
                new PreciosVistaPreviaResponse("token-abc", new BigDecimal("1000"),
                        null, null, List.of(), List.of(), 0));

        mockMvc.perform(get("/api/precios/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cotizacion").value(1000))
                .andExpect(jsonPath("$.productosDesactualizados").value(2))
                .andExpect(jsonPath("$.productosSinPrecioUsd").value(1));

        mockMvc.perform(get("/api/precios/lotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(post("/api/precios/vista-previa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PORCENTAJE\",\"valor\":10,"
                                + "\"alcance\":{\"tipo\":\"TODOS\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-abc"))
                .andExpect(jsonPath("$.cotizacion").value(1000));
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
