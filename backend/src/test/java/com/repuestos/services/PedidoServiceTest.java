package com.repuestos.services;

import com.repuestos.dto.PedidoCrearRequest;
import com.repuestos.dto.PedidoItemRequest;
import com.repuestos.dto.PedidoRechazarRequest;
import com.repuestos.dto.PedidoResponse;
import com.repuestos.models.Cliente;
import com.repuestos.models.Pedido;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.PagoRepository;
import com.repuestos.repositories.PedidoRepository;
import com.repuestos.repositories.ProductoRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.repositories.VentaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({PedidoService.class, ClienteService.class, VentaService.class,
        ProductoService.class, ConfiguracionService.class, AuditoriaService.class})
class PedidoServiceTest {

    @Autowired private PedidoService pedidoService;
    @Autowired private ClienteService clienteService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private EntityManager entityManager;

    private Usuario usuario;
    private Producto producto;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("pedido-cliente-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Cliente Pedidos")
                .email("pedidos@example.com")
                .rol(Usuario.Rol.CLIENTE)
                .activo(true)
                .build());
        producto = productoRepository.save(Producto.builder()
                .nombre("Producto Pedido")
                .precio(new BigDecimal("100.00"))
                .stockActual(10)
                .activo(true)
                .build());
    }

    private PedidoCrearRequest request(int cantidad, String telefono) {
        return request(cantidad, telefono, "EFECTIVO");
    }

    private PedidoCrearRequest request(int cantidad, String telefono, String metodoPago) {
        return new PedidoCrearRequest(
                List.of(new PedidoItemRequest(producto.getId(), cantidad)),
                telefono, "Sin sal", metodoPago);
    }

    private Usuario otroCliente() {
        return usuarioRepository.save(Usuario.builder()
                .username("otro-cliente-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Otro Cliente")
                .rol(Usuario.Rol.CLIENTE)
                .activo(true)
                .build());
    }

    private Producto recargarProducto() {
        entityManager.flush();
        entityManager.clear();
        return productoRepository.findById(producto.getId()).orElseThrow();
    }

    private Cliente recargarCliente(Long id) {
        entityManager.flush();
        entityManager.clear();
        return clienteRepository.findById(id).orElseThrow();
    }

    private Pedido recargarPedido(Long id) {
        entityManager.flush();
        entityManager.clear();
        return pedidoRepository.findById(id).orElseThrow();
    }

    @Test
    void crearPedidoNoReservaStockNiGeneraVentaNiDeuda() {
        PedidoResponse response = pedidoService.crear(
                usuario, request(3, "11 5555-1234"));

        assertNotNull(response.id());
        assertEquals("PENDIENTE", response.estado());
        assertEquals("EFECTIVO", response.metodoPago());
        assertEquals(0, new BigDecimal("300.00").compareTo(response.totalEstimado()));
        assertEquals(1, response.detalles().size());
        assertEquals("Producto Pedido", response.detalles().get(0).nombreProducto());

        assertEquals(10, recargarProducto().getStockActual());
        assertEquals(0, ventaRepository.count());
        Cliente cliente = recargarCliente(usuario.getCliente().getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(cliente.getSaldoCorriente()));
        assertEquals("11 5555-1234", cliente.getTelefono());
    }

    @Test
    void crearPedidoConCadaMetodoDisponibleGuardaElMetodoNormalizado() {
        int pendientesEsperados = 0;
        for (String metodo : List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA")) {
            PedidoResponse response = pedidoService.crear(
                    usuario, request(1, "11 5555-1234", "  " + metodo.toLowerCase() + "  "));

            assertEquals(metodo, response.metodoPago());
            assertEquals(metodo, recargarPedido(response.id()).getMetodoPago());
            pendientesEsperados++;
            assertEquals(pendientesEsperados, pedidoService.contarPendientes());
        }
    }

    @Test
    void crearPedidoRechazaMetodosNoDisponiblesParaComprasOnline() {
        String[] metodos = {"CUENTA_CORRIENTE", "OTRO", "", "   ", "BITCOIN", null};
        for (String metodo : metodos) {
            NegocioException error = assertThrows(NegocioException.class,
                    () -> pedidoService.crear(usuario, request(1, "11 5555-1234", metodo)));

            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
            assertEquals("Método de pago no disponible para compras online", error.getMessage());
        }
        assertEquals(0, pedidoRepository.count());
    }

    @Test
    void crearPedidoAcumulaCantidadesDeProductosRepetidosYValidaStock() {
        PedidoCrearRequest repetido = new PedidoCrearRequest(
                List.of(new PedidoItemRequest(producto.getId(), 6),
                        new PedidoItemRequest(producto.getId(), 5)),
                "11 5555-1234", null, "EFECTIVO");

        NegocioException error = assertThrows(NegocioException.class,
                () -> pedidoService.crear(usuario, repetido));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals(0, pedidoRepository.count());
    }

    @Test
    void crearPedidoAplicaDescuentoPorVolumenEnElEstimado() {
        producto.setStockActual(20);
        productoRepository.save(producto);

        PedidoResponse response = pedidoService.crear(
                usuario, request(11, "11 5555-1234"));

        assertEquals(0, new BigDecimal("935.00").compareTo(response.totalEstimado()));
        assertEquals(0, new BigDecimal("85.00").compareTo(
                response.detalles().get(0).precioUnitarioEstimado()));
    }

    @Test
    void clienteSoloPuedeVerSusPropiosPedidos() {
        PedidoResponse propio = pedidoService.crear(usuario, request(1, "11 5555-1234"));
        Usuario otro = otroCliente();
        PedidoResponse ajeno = pedidoService.crear(otro, request(1, "11 6666-1234"));

        List<PedidoResponse> mios = pedidoService.listarMios(usuario);

        assertEquals(List.of(propio.id()), mios.stream().map(PedidoResponse::id).toList());
        NegocioException error = assertThrows(NegocioException.class,
                () -> pedidoService.obtenerMio(ajeno.id(), usuario));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void convertirConCadaMetodoGeneraVentaPagadaDescuentaStockYRegistraPago() {
        int stockEsperado = 10;
        for (String metodo : List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA")) {
            assertEquals(stockEsperado, recargarProducto().getStockActual());

            PedidoResponse creado = pedidoService.crear(
                    usuario, request(2, "11 5555-1234", metodo));

            assertEquals(stockEsperado, recargarProducto().getStockActual(),
                    "crear el pedido no descuenta stock");

            PedidoResponse convertido = pedidoService.convertir(
                    creado.id(), usuario, false);

            assertEquals("CONVERTIDO", convertido.estado());
            assertEquals(metodo, convertido.metodoPago());
            assertNotNull(convertido.ventaId());

            Venta venta = ventaRepository.findById(convertido.ventaId()).orElseThrow();
            assertEquals(0, new BigDecimal("200.00").compareTo(venta.getTotal()));
            assertEquals(Venta.EstadoVenta.PAGADA, venta.getEstado());
            assertEquals("Pedido #" + creado.id(), venta.getObservaciones());

            stockEsperado -= 2;
            assertEquals(stockEsperado, recargarProducto().getStockActual());

            Cliente cliente = recargarCliente(usuario.getCliente().getId());
            assertEquals(0, BigDecimal.ZERO.compareTo(cliente.getSaldoCorriente()),
                    "la venta suma deuda y el pago la descuenta en la misma transacción");

            List<com.repuestos.models.Pago> pagos =
                    pagoRepository.findByVentaId(venta.getId());
            assertEquals(1, pagos.size(), "el pago figura en el historial del cliente");
            assertEquals(0, new BigDecimal("200.00").compareTo(pagos.get(0).getMonto()));
            assertEquals(metodo, pagos.get(0).getMetodoPago().name());
        }
    }

    @Test
    void convertirUsaElMetodoYLasLineasGuardadasIgnorandoCualquierAjuste() {
        PedidoResponse creado = pedidoService.crear(
                usuario, request(2, "11 5555-1234", "TARJETA"));

        PedidoResponse convertido = pedidoService.convertir(creado.id(), usuario, false);

        assertEquals("TARJETA", convertido.metodoPago());
        assertEquals(2, convertido.detalles().get(0).cantidad());
        assertEquals(0, new BigDecimal("200.00").compareTo(convertido.totalEstimado()));
        assertEquals(8, recargarProducto().getStockActual());
        Venta venta = ventaRepository.findById(convertido.ventaId()).orElseThrow();
        assertEquals(0, new BigDecimal("200.00").compareTo(venta.getTotal()));
    }

    @Test
    void noSePuedeConvertirUnPedidoAnteriorSinMetodoDePago() {
        PedidoResponse creado = pedidoService.crear(usuario, request(1, "11 5555-1234"));
        Pedido pedido = pedidoRepository.findById(creado.id()).orElseThrow();
        pedido.setMetodoPago(null);
        pedidoRepository.save(pedido);
        entityManager.flush();
        entityManager.clear();

        NegocioException error = assertThrows(NegocioException.class,
                () -> pedidoService.convertir(creado.id(), usuario, false));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("Este pedido es anterior a la selección de método de pago y no puede "
                + "aprobarse: rechazalo y pedile al cliente que lo repita", error.getMessage());
        assertEquals(Pedido.EstadoPedido.PENDIENTE, recargarPedido(creado.id()).getEstado());
        assertEquals(0, ventaRepository.count());
        assertEquals(10, recargarProducto().getStockActual());
    }

    @Test
    void noSePuedeConvertirDosVecesElMismoPedido() {
        PedidoResponse creado = pedidoService.crear(usuario, request(1, "11 5555-1234"));
        pedidoService.convertir(creado.id(), usuario, false);

        NegocioException error = assertThrows(NegocioException.class,
                () -> pedidoService.convertir(creado.id(), usuario, false));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals(1, ventaRepository.count());
    }

    @Test
    void stockInsuficienteAlConvertirNombraElProductoYDejaElPedidoPendiente() {
        PedidoResponse creado = pedidoService.crear(usuario, request(2, "11 5555-1234"));

        producto.setStockActual(1);
        productoRepository.save(producto);
        entityManager.flush();
        entityManager.clear();

        NegocioException error = assertThrows(NegocioException.class,
                () -> pedidoService.convertir(creado.id(), usuario, false));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("Stock insuficiente para: Producto Pedido", error.getMessage());
        assertEquals(Pedido.EstadoPedido.PENDIENTE, recargarPedido(creado.id()).getEstado());
        assertEquals(0, ventaRepository.count());
        assertEquals(1, recargarProducto().getStockActual());
    }

    @Test
    void rechazarPedidoNoGeneraVentaNiModificaStock() {
        PedidoResponse creado = pedidoService.crear(usuario, request(2, "11 5555-1234"));

        PedidoResponse rechazado = pedidoService.rechazar(
                creado.id(), new PedidoRechazarRequest("Falta de stock"), usuario, false);

        assertEquals("RECHAZADO", rechazado.estado());
        assertEquals("Falta de stock", rechazado.motivoRechazo());
        assertNull(rechazado.ventaId());
        assertEquals(10, recargarProducto().getStockActual());
        assertEquals(0, ventaRepository.count());
    }

    @Test
    void clientePuedeCancelarSoloSuPedido() {
        PedidoResponse propio = pedidoService.crear(usuario, request(1, "11 5555-1234"));
        Usuario otro = otroCliente();
        PedidoResponse ajeno = pedidoService.crear(otro, request(1, "11 6666-1234"));

        NegocioException error = assertThrows(NegocioException.class,
                () -> pedidoService.cancelarMio(ajeno.id(), usuario));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());

        PedidoResponse cancelado = pedidoService.cancelarMio(propio.id(), usuario);
        assertEquals("CANCELADO", cancelado.estado());
        assertEquals(10, recargarProducto().getStockActual());
    }

    @Test
    void noPermiteMasDeDiezPedidosPendientes() {
        for (int i = 0; i < 10; i++) {
            pedidoService.crear(usuario, request(1, "11 5555-1234"));
        }

        NegocioException error = assertThrows(NegocioException.class,
                () -> pedidoService.crear(usuario, request(1, "11 5555-1234")));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals(10, pedidoRepository.count());
    }

    @Test
    void noPermitePedirProductosInactivosOSinStock() {
        producto.setActivo(false);
        productoRepository.save(producto);
        NegocioException inactivo = assertThrows(NegocioException.class,
                () -> pedidoService.crear(usuario, request(1, "11 5555-1234")));
        assertEquals(HttpStatus.BAD_REQUEST, inactivo.getStatus());

        producto.setActivo(true);
        producto.setStockActual(1);
        productoRepository.save(producto);
        NegocioException sinStock = assertThrows(NegocioException.class,
                () -> pedidoService.crear(usuario, request(2, "11 5555-1234")));
        assertEquals(HttpStatus.CONFLICT, sinStock.getStatus());
    }

    @Test
    void pedidoPendienteNoAfectaDashboardNiVentas() {
        pedidoService.crear(usuario, request(4, "11 5555-1234"));

        assertEquals(0, ventaRepository.count());
        assertEquals(1, pedidoRepository.countByEstado(Pedido.EstadoPedido.PENDIENTE),
                "el pedido existe pero no se transforma en venta");
        assertEquals(1, pedidoService.contarPendientes());
    }

    @Test
    void rechazarYCancelarNoTocanStockNiSaldo() {
        PedidoResponse aRechazar = pedidoService.crear(usuario, request(2, "11 5555-1234"));
        PedidoResponse aCancelar = pedidoService.crear(usuario, request(3, "11 5555-1234"));

        pedidoService.rechazar(aRechazar.id(),
                new PedidoRechazarRequest("No tenemos ese producto"), usuario, false);
        pedidoService.cancelarMio(aCancelar.id(), usuario);

        assertEquals(10, recargarProducto().getStockActual());
        assertEquals(0, ventaRepository.count());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                recargarCliente(usuario.getCliente().getId()).getSaldoCorriente()));
        assertEquals(0, pedidoService.contarPendientes());
    }

    @Test
    void noSePuedePedirSiLaCuentaDeClienteEstaInhabilitada() {
        pedidoService.crear(usuario, request(1, "11 5555-1234"));
        Cliente cliente = recargarCliente(usuario.getCliente().getId());
        cliente.setActivo(false);
        clienteRepository.save(cliente);
        entityManager.flush();
        entityManager.clear();
        Usuario usuarioRecargado = usuarioRepository.findById(usuario.getId()).orElseThrow();

        NegocioException error = assertThrows(NegocioException.class,
                () -> pedidoService.crear(usuarioRecargado, request(1, "11 5555-1234")));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("Tu cuenta de cliente está inhabilitada, contactanos", error.getMessage());
    }

    @Test
    void elTelefonoSeGuardaEnLaFichaDelCliente() {
        pedidoService.crear(usuario, request(1, "11 5555-1234"));
        assertEquals("11 5555-1234",
                recargarCliente(usuario.getCliente().getId()).getTelefono());

        pedidoService.crear(usuario, request(1, "11 7777-9999"));
        assertEquals("11 7777-9999",
                recargarCliente(usuario.getCliente().getId()).getTelefono());
        assertEquals(1, clienteRepository.count(), "nunca se crea un cliente duplicado");
    }
}
