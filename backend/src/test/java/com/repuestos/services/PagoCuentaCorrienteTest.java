package com.repuestos.services;

import com.repuestos.models.Cliente;
import com.repuestos.models.DetalleVenta;
import com.repuestos.models.Pago;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.PagoRepository;
import com.repuestos.repositories.ProductoRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.repositories.VentaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({VentaService.class, ClienteService.class, ProductoService.class, ConfiguracionService.class})
class PagoCuentaCorrienteTest {

    @Autowired private VentaService ventaService;
    @Autowired private ClienteService clienteService;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private EntityManager entityManager;

    private Usuario usuario;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("tester-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Tester")
                .rol(Usuario.Rol.ADMIN)
                .build());
        cliente = clienteRepository.save(Cliente.builder()
                .nombre("Cliente Cuenta Corriente")
                .saldoCorriente(new BigDecimal("0.00"))
                .build());
    }

    private Venta crearVenta(String total, LocalDateTime fecha) {
        return ventaRepository.save(Venta.builder()
                .usuario(usuario)
                .cliente(cliente)
                .fecha(fecha)
                .total(new BigDecimal(total))
                .estado(Venta.EstadoVenta.PENDIENTE)
                .build());
    }

    private void fijarSaldoCliente(String saldo) {
        entityManager.clear();
        Cliente c = clienteRepository.findById(cliente.getId()).orElseThrow();
        c.setSaldoCorriente(new BigDecimal(saldo));
        clienteRepository.save(c);
        entityManager.flush();
    }

    private void pagarCuentaCorrienteComoRequest(String monto) {
        entityManager.clear();
        ventaService.registrarPagoCuentaCorriente(cliente.getId(), new BigDecimal(monto), "EFECTIVO");
        entityManager.flush(); // simula el commit del request
    }

    private BigDecimal saldoCliente() {
        entityManager.clear();
        return clienteRepository.findById(cliente.getId()).orElseThrow().getSaldoCorriente();
    }

    private Venta recargarVenta(Long id) {
        entityManager.clear();
        return ventaRepository.findById(id).orElseThrow();
    }

    private BigDecimal montoPagadoReal(Long ventaId) {
        return pagoRepository.findByVentaId(ventaId).stream()
                .map(com.repuestos.models.Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void pagoCuentaCorrienteReparteEntreVariasVentasMasAntiguaPrimero() {
        Venta v1 = crearVenta("50.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        Venta v2 = crearVenta("50.00", LocalDateTime.of(2026, 1, 2, 10, 0));
        fijarSaldoCliente("100.00");

        pagarCuentaCorrienteComoRequest("70.00");

        Venta r1 = recargarVenta(v1.getId());
        Venta.EstadoVenta estado1 = r1.getEstado();
        BigDecimal pagado1 = r1.getMontoPagado();
        Venta r2 = recargarVenta(v2.getId());
        Venta.EstadoVenta estado2 = r2.getEstado();
        BigDecimal pagado2 = r2.getMontoPagado();
        BigDecimal pendiente2 = r2.getSaldoPendiente();

        assertEquals(Venta.EstadoVenta.PAGADA, estado1, "la venta mas antigua debe quedar PAGADA");
        assertEquals(Venta.EstadoVenta.PARCIAL, estado2, "la siguiente debe quedar PARCIAL");
        assertEquals(0, new BigDecimal("50.00").compareTo(pagado1));
        assertEquals(0, new BigDecimal("20.00").compareTo(pagado2));
        assertEquals(0, new BigDecimal("30.00").compareTo(pendiente2));
        assertEquals(0, new BigDecimal("30.00").compareTo(saldoCliente()),
                "el saldo del cliente debe bajar exactamente 70.00");
    }

    @Test
    void pagoCuentaCorrienteDescuentaElSaldoUnaSolaVez() {
        Venta v1 = crearVenta("30.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        Venta v2 = crearVenta("30.00", LocalDateTime.of(2026, 1, 2, 10, 0));
        fijarSaldoCliente("60.00");

        pagarCuentaCorrienteComoRequest("45.00");

        assertEquals(0, new BigDecimal("15.00").compareTo(saldoCliente()),
                "el saldoCorriente debe bajar exactamente una vez por el monto total pagado");
        assertEquals(0, new BigDecimal("30.00").compareTo(montoPagadoReal(v1.getId())));
        assertEquals(0, new BigDecimal("15.00").compareTo(montoPagadoReal(v2.getId())));
        assertEquals(Venta.EstadoVenta.PAGADA, recargarVenta(v1.getId()).getEstado());
        assertEquals(Venta.EstadoVenta.PARCIAL, recargarVenta(v2.getId()).getEstado());
    }

    @Test
    void pagoCuentaCorrienteConDeudaManualUsaRemanenteSinVenta() {
        Venta v1 = crearVenta("40.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        // saldo 100 = 40 de la venta + 60 de deuda manual (tipo "sumar")
        fijarSaldoCliente("100.00");

        pagarCuentaCorrienteComoRequest("80.00");

        Venta r1 = recargarVenta(v1.getId());
        assertEquals(Venta.EstadoVenta.PAGADA, r1.getEstado());
        assertEquals(0, new BigDecimal("40.00").compareTo(r1.getMontoPagado()));
        assertEquals(0, new BigDecimal("20.00").compareTo(saldoCliente()));

        entityManager.clear();
        Long pagosSinVenta = pagoRepository.findPagosPorCliente(cliente.getId()).stream()
                .filter(p -> p.getVenta() == null)
                .count();
        assertEquals(1L, pagosSinVenta, "el remanente debe registrarse como pago sin venta");
    }

    @Test
    void pagoCuentaCorrienteIgnoraVentasAnuladas() {
        Venta anulada = crearVenta("50.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        Venta abierta = crearVenta("50.00", LocalDateTime.of(2026, 1, 2, 10, 0));
        entityManager.clear();
        ventaService.anularVenta(anulada.getId());
        entityManager.flush();
        // Estas ventas se crean directo por repositorio, sin el flujo que suma la deuda
        // al cliente. Se fija el saldo para simular el estado real antes del pago.
        fijarSaldoCliente("100.00");

        pagarCuentaCorrienteComoRequest("50.00");

        Venta rAnulada = recargarVenta(anulada.getId());
        Venta rAbierta = recargarVenta(abierta.getId());
        assertEquals(Venta.EstadoVenta.ANULADA, rAnulada.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(montoPagadoReal(anulada.getId())),
                "la venta anulada no debe recibir pagos");
        assertEquals(Venta.EstadoVenta.PAGADA, rAbierta.getEstado(),
                "el pago debe aplicarse a la unica venta abierta");
        assertEquals(0, new BigDecimal("50.00").compareTo(rAbierta.getMontoPagado()));
        assertEquals(0, new BigDecimal("50.00").compareTo(saldoCliente()));
    }

    @Test
    void pagoCuentaCorrienteMayorAlSaldoRechazadoYSinCambios() {
        Venta v1 = crearVenta("50.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        fijarSaldoCliente("50.00");

        entityManager.clear();
        assertThrows(IllegalArgumentException.class,
                () -> ventaService.registrarPagoCuentaCorriente(cliente.getId(), new BigDecimal("60.00"), "EFECTIVO"));

        Venta r1 = recargarVenta(v1.getId());
        assertEquals(Venta.EstadoVenta.PENDIENTE, r1.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(montoPagadoReal(v1.getId())));
        assertEquals(0, new BigDecimal("50.00").compareTo(saldoCliente()));
    }

    @Test
    void ajustarSaldoRestarDelegaEnPagoDeCuentaCorriente() {
        Venta v1 = crearVenta("50.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        Venta v2 = crearVenta("50.00", LocalDateTime.of(2026, 1, 2, 10, 0));
        fijarSaldoCliente("100.00");

        entityManager.clear();
        Cliente resultado = clienteService.ajustarSaldo(cliente.getId(), new BigDecimal("70.00"), "restar", "TRANSFERENCIA");

        assertEquals(0, new BigDecimal("30.00").compareTo(resultado.getSaldoCorriente()));
        entityManager.flush();
        Venta r1 = recargarVenta(v1.getId());
        Venta.EstadoVenta estado1 = r1.getEstado();
        BigDecimal pagado1 = r1.getMontoPagado();
        Venta r2 = recargarVenta(v2.getId());
        Venta.EstadoVenta estado2 = r2.getEstado();
        BigDecimal pagado2 = r2.getMontoPagado();

        assertEquals(Venta.EstadoVenta.PAGADA, estado1, "la venta mas antigua debe quedar PAGADA");
        assertEquals(Venta.EstadoVenta.PARCIAL, estado2, "la siguiente debe quedar PARCIAL");
        assertEquals(0, new BigDecimal("50.00").compareTo(pagado1));
        assertEquals(0, new BigDecimal("20.00").compareTo(pagado2));
    }

    @Test
    void ajustarSaldoRestarMayorAlSaldoRechazado() {
        fijarSaldoCliente("30.00");

        entityManager.clear();
        assertThrows(IllegalArgumentException.class,
                () -> clienteService.ajustarSaldo(cliente.getId(), new BigDecimal("50.00"), "restar", "EFECTIVO"));
        assertEquals(0, new BigDecimal("30.00").compareTo(saldoCliente()));
    }

    @Test
    void ajustarSaldoSumarNoCambia() {
        entityManager.clear();
        Cliente resultado = clienteService.ajustarSaldo(cliente.getId(), new BigDecimal("25.00"), "sumar", null);
        assertEquals(0, new BigDecimal("25.00").compareTo(resultado.getSaldoCorriente()));
    }

    @Test
    void pagoCuentaCorrienteReasignaExcedenteDeVentaSobrepagada() {
        Venta origen = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        Venta abierta = crearVenta("100.00", LocalDateTime.of(2026, 1, 2, 10, 0));
        origen.setEstado(Venta.EstadoVenta.PAGADA);
        ventaRepository.save(origen);
        pagoRepository.save(Pago.builder()
                .venta(origen)
                .cliente(cliente)
                .monto(new BigDecimal("120.00"))
                .metodoPago(Pago.MetodoPago.EFECTIVO)
                .fecha(LocalDateTime.now())
                .build());
        // 200 de ventas - 120 pagados = 80 de deuda real
        fijarSaldoCliente("80.00");

        pagarCuentaCorrienteComoRequest("80.00");

        Venta rOrigen = recargarVenta(origen.getId());
        Venta.EstadoVenta estadoOrigen = rOrigen.getEstado();
        BigDecimal pendienteOrigen = rOrigen.getSaldoPendiente();
        Venta rAbierta = recargarVenta(abierta.getId());
        assertEquals(Venta.EstadoVenta.PAGADA, estadoOrigen);
        assertEquals(0, new BigDecimal("100.00").compareTo(montoPagadoReal(origen.getId())),
                "el excedente de 20 debe salir de la venta sobrepagada");
        assertEquals(Venta.EstadoVenta.PAGADA, rAbierta.getEstado());
        assertEquals(0, new BigDecimal("100.00").compareTo(montoPagadoReal(abierta.getId())));
        assertEquals(0, BigDecimal.ZERO.compareTo(pendienteOrigen));
        assertEquals(0, BigDecimal.ZERO.compareTo(rAbierta.getSaldoPendiente()));
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoCliente()));
    }

    @Test
    void anularVentaCancelaDeudaPendienteDelCliente() {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        fijarSaldoCliente("100.00");

        entityManager.clear();
        ventaService.anularVenta(venta.getId());
        entityManager.flush();

        assertEquals(0, BigDecimal.ZERO.compareTo(saldoCliente()),
                "la deuda de una venta anulada no debe quedar en la cuenta corriente");
    }

    @Test
    void crearVentaConPagoUsaElTotalCalculadoPorElBackend() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Quemador")
                .precio(new BigDecimal("8500.00"))
                .stockActual(20)
                .build());
        DetalleVenta detalle = DetalleVenta.builder()
                .producto(producto)
                .cantidad(16)
                .build();
        Venta venta = Venta.builder()
                .usuario(usuario)
                .build();

        Venta creada = ventaService.crearVentaConPago(
                venta, List.of(detalle), cliente.getId(), "EFECTIVO");
        entityManager.flush();

        // 16 unidades superan el umbral: 8500 * 0.85 = 7225 por unidad
        assertEquals(0, new BigDecimal("115600.00").compareTo(creada.getTotal()));
        assertEquals(0, new BigDecimal("115600.00").compareTo(montoPagadoReal(creada.getId())));
        assertEquals(Venta.EstadoVenta.PAGADA, recargarVenta(creada.getId()).getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoCliente()));
    }
}
