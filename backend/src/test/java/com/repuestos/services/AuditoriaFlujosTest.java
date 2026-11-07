package com.repuestos.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repuestos.dto.DatosTransferenciaRequest;
import com.repuestos.dto.PedidoCrearRequest;
import com.repuestos.dto.PedidoItemRequest;
import com.repuestos.dto.PedidoResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Cliente;
import com.repuestos.models.DetalleVenta;
import com.repuestos.models.LogActividad;
import com.repuestos.models.Pago;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.ProductoRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.repositories.VentaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({VentaService.class, ClienteService.class, ProductoService.class,
        ConfiguracionService.class, PedidoService.class, AuditoriaService.class,
        FotoPerfilService.class, AvatarStorageService.class})
class AuditoriaFlujosTest {

    @Autowired private VentaService ventaService;
    @Autowired private PedidoService pedidoService;
    @Autowired private ConfiguracionService configuracionService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private LogActividadRepository logActividadRepository;
    @Autowired private EntityManager entityManager;

    private Usuario admin;
    private Usuario clienteUsuario;
    private Cliente cliente;
    private Producto producto;

    @BeforeEach
    void setUp() {
        admin = usuarioRepository.save(Usuario.builder()
                .username("flujo-admin-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Admin Flujos")
                .rol(Usuario.Rol.ADMIN)
                .activo(true)
                .build());
        clienteUsuario = usuarioRepository.save(Usuario.builder()
                .username("flujo-cliente-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Cliente Flujos")
                .email("flujo@example.com")
                .rol(Usuario.Rol.CLIENTE)
                .activo(true)
                .build());
        cliente = clienteRepository.save(Cliente.builder()
                .nombre("Cliente Flujos")
                .saldoCorriente(BigDecimal.ZERO)
                .activo(true)
                .build());
        producto = productoRepository.save(Producto.builder()
                .nombre("Producto Flujos")
                .precio(new BigDecimal("100.00"))
                .stockActual(50)
                .activo(true)
                .build());

        autenticar(admin);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))));
    }

    private long contar(AccionAuditoria accion) {
        entityManager.flush();
        return logActividadRepository.findAll().stream()
                .filter(log -> accion.name().equals(log.getAccion()))
                .count();
    }

    private LogActividad buscar(AccionAuditoria accion) {
        entityManager.flush();
        return logActividadRepository.findAll().stream()
                .filter(log -> accion.name().equals(log.getAccion()))
                .findFirst()
                .orElseThrow();
    }

    private DetalleVenta detalle(int cantidad) {
        return DetalleVenta.builder().producto(producto).cantidad(cantidad).build();
    }

    private Venta venta(String observaciones) {
        return Venta.builder().usuario(admin).observaciones(observaciones).build();
    }

    @Test
    void ventaConPagoInicialRegistraUnaSolaVentaSinPagoDuplicado() throws Exception {
        Venta creada = ventaService.crearVentaConPago(
                venta(null), List.of(detalle(2)), cliente.getId(), "EFECTIVO");

        assertEquals(1, contar(AccionAuditoria.VENTA_CREADA));
        assertEquals(0, contar(AccionAuditoria.PAGO_REGISTRADO));

        LogActividad log = buscar(AccionAuditoria.VENTA_CREADA);
        assertEquals(creada.getId(), log.getEntidadId());
        assertEquals("ADMIN", log.getUsuarioRol());
        JsonNode detalle = new ObjectMapper().readTree(log.getDetalle());
        assertEquals(0, new BigDecimal("200.00")
                .compareTo(detalle.get("pagoInicial").get("monto").decimalValue()));
        assertEquals("EFECTIVO", detalle.get("metodoPago").asText());
    }

    @Test
    void ventaDesdePedidoGuardaElOrigenYNoDuplicaPago() throws Exception {
        Venta creada = ventaService.crearVentaConPago(
                venta("Pedido #77"), List.of(detalle(1)), cliente.getId(), "TARJETA");

        LogActividad log = buscar(AccionAuditoria.VENTA_CREADA);
        JsonNode detalle = new ObjectMapper().readTree(log.getDetalle());

        assertEquals(creada.getId(), log.getEntidadId());
        assertEquals("pedido #77", detalle.get("origen").asText());
        assertTrue(log.getResumen().contains("origen: pedido #77"));
        assertEquals(0, contar(AccionAuditoria.PAGO_REGISTRADO));
    }

    @Test
    void registrarPagoYAnularVentaGeneranSusLogs() {
        Venta creada = ventaService.crearVentaConPago(
                venta(null), List.of(detalle(1)), cliente.getId(), "CUENTA_CORRIENTE");
        logActividadRepository.deleteAll();
        entityManager.flush();

        Pago pago = Pago.builder()
                .monto(new BigDecimal("40.00"))
                .metodoPago(Pago.MetodoPago.EFECTIVO)
                .build();
        ventaService.registrarPago(creada.getId(), pago);

        assertEquals(1, contar(AccionAuditoria.PAGO_REGISTRADO));
        assertEquals(creada.getId(), buscar(AccionAuditoria.PAGO_REGISTRADO).getEntidadId());

        logActividadRepository.deleteAll();
        entityManager.flush();

        ventaService.anularVenta(creada.getId());

        assertEquals(1, contar(AccionAuditoria.VENTA_ANULADA));
        assertEquals(creada.getId(), buscar(AccionAuditoria.VENTA_ANULADA).getEntidadId());
    }

    @Test
    void pedidoCreadoAprobadoYVentaGeneranUnLogCadaUno() {
        PedidoCrearRequest request = new PedidoCrearRequest(
                List.of(new PedidoItemRequest(producto.getId(), 2)),
                "11 5555-1234", "Sin sal", "EFECTIVO");

        PedidoResponse pedido = pedidoService.crear(clienteUsuario, request);
        assertEquals(1, contar(AccionAuditoria.PEDIDO_CREADO));

        autenticar(admin);
        PedidoResponse convertido = pedidoService.convertir(pedido.id(), admin, true);

        assertNotNull(convertido.ventaId());
        assertEquals(1, contar(AccionAuditoria.PEDIDO_APROBADO));
        assertEquals(1, contar(AccionAuditoria.VENTA_CREADA));
        assertEquals(0, contar(AccionAuditoria.PAGO_REGISTRADO));
        assertTrue(buscar(AccionAuditoria.PEDIDO_CREADO).getResumen()
                .contains("Creó el pedido #" + pedido.id()));
        assertTrue(buscar(AccionAuditoria.PEDIDO_APROBADO).getResumen()
                .contains("venta #" + convertido.ventaId()));
    }

    @Test
    void configuracionSoloRegistraCambiosReales() {
        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_WHATSAPP_NUMBER, "5491111111111", "WhatsApp");
        assertEquals(1, contar(AccionAuditoria.CONFIG_EDITADA));

        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_WHATSAPP_NUMBER, "5491111111111", "WhatsApp");
        assertEquals(1, contar(AccionAuditoria.CONFIG_EDITADA));

        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_WHATSAPP_NUMBER, "5492222222222", "WhatsApp");
        assertEquals(2, contar(AccionAuditoria.CONFIG_EDITADA));
    }

    @Test
    void datosDeTransferenciaTienenLogEspecificoYNoGenerico() {
        configuracionService.guardarDatosTransferencia(new DatosTransferenciaRequest(
                "1111111111111111111111", "nuevo.alias", "Nuevo Titular"));

        assertEquals(1, contar(AccionAuditoria.CONFIG_TRANSFERENCIA_EDITADA));
        assertEquals(0, contar(AccionAuditoria.CONFIG_EDITADA));

        configuracionService.guardarDatosTransferencia(new DatosTransferenciaRequest(
                "1111111111111111111111", "nuevo.alias", "Nuevo Titular"));
        assertEquals(1, contar(AccionAuditoria.CONFIG_TRANSFERENCIA_EDITADA));
    }
}
