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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import({VentaService.class, ClienteService.class, ProductoService.class, ConfiguracionService.class, AuditoriaService.class, FotoPerfilService.class, AvatarStorageService.class})
class ConciliacionCuentaCorrienteTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

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
    private BigDecimal deudaManual;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("tester-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Tester")
                .rol(Usuario.Rol.ADMIN)
                .build());
        cliente = clienteRepository.save(Cliente.builder()
                .nombre("Cliente Conciliacion")
                .saldoCorriente(new BigDecimal("0.00"))
                .build());
        deudaManual = BigDecimal.ZERO.setScale(2);
    }

    private Venta ventaCc(String total, LocalDateTime fecha) {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto " + SEQ.incrementAndGet())
                .precio(new BigDecimal(total))
                .stockActual(100)
                .build());
        DetalleVenta detalle = DetalleVenta.builder()
                .producto(producto)
                .cantidad(1)
                .build();
        Venta venta = Venta.builder().usuario(usuario).build();
        Venta creada = ventaService.crearVenta(venta, List.of(detalle), cliente.getId());
        creada.setFecha(fecha);
        ventaRepository.save(creada);
        flushAndClear();
        return creada;
    }

    private Venta ventaContado(String total) {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto " + SEQ.incrementAndGet())
                .precio(new BigDecimal(total))
                .stockActual(100)
                .build());
        DetalleVenta detalle = DetalleVenta.builder()
                .producto(producto)
                .cantidad(1)
                .build();
        Venta venta = Venta.builder().usuario(usuario).build();
        Venta creada = ventaService.crearVentaConPago(venta, List.of(detalle), cliente.getId(), "EFECTIVO");
        flushAndClear();
        return creada;
    }

    private void pagoCuentaCorriente(String monto) {
        ventaService.registrarPagoCuentaCorriente(cliente.getId(), new BigDecimal(monto), "EFECTIVO");
        flushAndClear();
    }

    private void pagoVenta(Long ventaId, String monto) {
        ventaService.registrarPago(ventaId, Pago.builder()
                .monto(new BigDecimal(monto))
                .metodoPago(Pago.MetodoPago.EFECTIVO)
                .build());
        flushAndClear();
    }

    private void sumarDeudaManual(String monto) {
        clienteService.ajustarSaldo(cliente.getId(), new BigDecimal(monto), "sumar", null);
        deudaManual = deudaManual.add(new BigDecimal(monto));
        flushAndClear();
    }

    private void anular(Long ventaId) {
        ventaService.anularVenta(ventaId);
        flushAndClear();
    }

    private void fijarSaldo(String saldo) {
        Cliente actual = clienteRepository.findById(cliente.getId()).orElseThrow();
        actual.setSaldoCorriente(new BigDecimal(saldo));
        clienteRepository.save(actual);
        flushAndClear();
    }

    private void pagoLegacySinVenta(String monto) {
        Cliente actual = clienteRepository.findById(cliente.getId()).orElseThrow();
        pagoRepository.save(Pago.builder()
                .cliente(actual)
                .monto(new BigDecimal(monto))
                .metodoPago(Pago.MetodoPago.EFECTIVO)
                .referencia("Pago de cuenta corriente (legacy)")
                .fecha(LocalDateTime.now())
                .build());
        flushAndClear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private BigDecimal saldo() {
        entityManager.clear();
        return clienteRepository.findById(cliente.getId()).orElseThrow().getSaldoCorriente()
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal pendienteVentas() {
        BigDecimal total = BigDecimal.ZERO;
        for (Venta venta : ventaRepository.findByClienteId(cliente.getId())) {
            if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
                continue;
            }
            BigDecimal pagado = pagoRepository.sumMontoPagadoPorVenta(venta.getId());
            if (pagado == null) {
                pagado = BigDecimal.ZERO;
            }
            total = total.add(venta.getTotal().subtract(pagado));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal pagosSinVenta() {
        return pagoRepository.findPagosPorCliente(cliente.getId()).stream()
                .filter(p -> p.getVenta() == null)
                .map(Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Venta venta(Long id) {
        entityManager.clear();
        return ventaRepository.findById(id).orElseThrow();
    }

    private void informe(String escenario, String esperado) {
        BigDecimal saldo = saldo();
        BigDecimal pendiente = pendienteVentas();
        BigDecimal sinVenta = pagosSinVenta();
        BigDecimal esperadoLedger = pendiente.add(deudaManual).subtract(sinVenta);
        BigDecimal saldoMenosVentas = saldo.subtract(pendiente);
        System.out.println("[CONCILIACION] " + escenario
                + " | esperado=" + esperado
                + " | saldo=" + saldo
                + " | pendienteVentas=" + pendiente
                + " | deudaManual=" + deudaManual
                + " | pagosSinVenta=" + sinVenta
                + " | saldo-pendienteVentas=" + saldoMenosVentas
                + " | saldo-esperadoLedger=" + saldo.subtract(esperadoLedger));
        assertEquals(0, saldo.compareTo(esperadoLedger),
                escenario + ": saldo no concilia con el ledger (saldo=" + saldo
                        + ", esperado=" + esperadoLedger + ")");
    }

    @Test
    void s1VentaConClientePagadaAlContado() {
        Venta venta = ventaContado("100.00");

        assertEquals(Venta.EstadoVenta.PAGADA, venta(venta.getId()).getEstado());
        assertEquals(0, saldo().compareTo(new BigDecimal("0.00")));
        informe("S1 venta contado", "saldo 0, venta PAGADA");
    }

    @Test
    void s2VentaACuentaCorriente() {
        Venta venta = ventaCc("100.00", LocalDateTime.now());

        assertEquals(Venta.EstadoVenta.PENDIENTE, venta(venta.getId()).getEstado());
        assertEquals(0, saldo().compareTo(new BigDecimal("100.00")));
        informe("S2 venta CC", "saldo 100, venta PENDIENTE");
    }

    @Test
    void s3PagoMenorALaVentaMasAntigua() {
        Venta v1 = ventaCc("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        Venta v2 = ventaCc("100.00", LocalDateTime.of(2026, 1, 2, 10, 0));

        pagoCuentaCorriente("40.00");

        assertEquals(Venta.EstadoVenta.PARCIAL, venta(v1.getId()).getEstado());
        assertEquals(Venta.EstadoVenta.PENDIENTE, venta(v2.getId()).getEstado());
        assertEquals(0, saldo().compareTo(new BigDecimal("160.00")));
        informe("S3 pago CC 40 sobre 2x100", "saldo 160, v1 PARCIAL, v2 PENDIENTE");
    }

    @Test
    void s4PagoQueCubreVariasVentas() {
        Venta v1 = ventaCc("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        Venta v2 = ventaCc("100.00", LocalDateTime.of(2026, 1, 2, 10, 0));

        pagoCuentaCorriente("150.00");

        assertEquals(Venta.EstadoVenta.PAGADA, venta(v1.getId()).getEstado());
        assertEquals(Venta.EstadoVenta.PARCIAL, venta(v2.getId()).getEstado());
        assertEquals(0, saldo().compareTo(new BigDecimal("50.00")));
        informe("S4 pago CC 150 sobre 2x100", "saldo 50, v1 PAGADA, v2 PARCIAL");
    }

    @Test
    void s5DeudaManualYPagoMayorALasVentas() {
        ventaCc("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        sumarDeudaManual("30.00");

        pagoCuentaCorriente("120.00");

        assertEquals(0, saldo().compareTo(new BigDecimal("10.00")));
        assertEquals(0, pagosSinVenta().compareTo(new BigDecimal("20.00")));
        informe("S5 deuda manual 30 + pago CC 120", "saldo 10, pago sin venta 20");
    }

    @Test
    void s6AnularVentaPendiente() {
        Venta venta = ventaCc("100.00", LocalDateTime.now());

        anular(venta.getId());

        assertEquals(Venta.EstadoVenta.ANULADA, venta(venta.getId()).getEstado());
        assertEquals(0, saldo().compareTo(new BigDecimal("0.00")));
        informe("S6 anular venta PENDIENTE", "saldo 0, venta ANULADA");
    }

    @Test
    void s7AnularVentaParcialYVentaPagadaConPagos() {
        Venta parcial = ventaCc("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        pagoCuentaCorriente("40.00");
        anular(parcial.getId());

        Venta pagada = ventaCc("50.00", LocalDateTime.of(2026, 1, 2, 10, 0));
        pagoCuentaCorriente("50.00");
        anular(pagada.getId());

        assertEquals(Venta.EstadoVenta.ANULADA, venta(parcial.getId()).getEstado());
        assertEquals(Venta.EstadoVenta.ANULADA, venta(pagada.getId()).getEstado());
        assertEquals(0, saldo().compareTo(new BigDecimal("0.00")));
        informe("S7 anular PARCIAL y PAGADA con pagos", "saldo 0, ventas ANULADAS");
    }

    @Test
    void s8PagoConDecimalesEscala2() {
        Venta venta = ventaCc("100.00", LocalDateTime.now());

        pagoVenta(venta.getId(), "33.335");

        Venta recargada = venta(venta.getId());
        assertEquals(Venta.EstadoVenta.PARCIAL, recargada.getEstado());
        assertEquals(0, new BigDecimal("33.34").compareTo(recargada.getMontoPagado()));
        assertEquals(0, saldo().compareTo(new BigDecimal("66.66")));
        informe("S8 pago 33.335 redondeado", "pagado 33.34, saldo 66.66");
    }

    @Test
    void s9DosPagosSeguidosSobreLaMismaVenta() {
        Venta venta = ventaCc("100.00", LocalDateTime.now());

        pagoVenta(venta.getId(), "40.00");
        pagoVenta(venta.getId(), "60.00");

        Venta recargada = venta(venta.getId());
        assertEquals(Venta.EstadoVenta.PAGADA, recargada.getEstado());
        assertEquals(0, new BigDecimal("100.00").compareTo(recargada.getMontoPagado()));
        assertEquals(0, saldo().compareTo(new BigDecimal("0.00")));
        informe("S9 dos pagos 40+60", "venta PAGADA, saldo 0");
    }

    @Test
    void s10PagoLegacySinVentaYLuegoPagoNuevo() {
        Venta venta = ventaCc("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        pagoLegacySinVenta("40.00");
        fijarSaldo("60.00");

        pagoCuentaCorriente("60.00");

        Venta recargada = venta(venta.getId());
        assertEquals(Venta.EstadoVenta.PARCIAL, recargada.getEstado());
        assertEquals(0, new BigDecimal("60.00").compareTo(recargada.getMontoPagado()));
        assertEquals(0, saldo().compareTo(new BigDecimal("0.00")));
        assertEquals(0, pagosSinVenta().compareTo(new BigDecimal("40.00")));
        informe("S10 pago legacy sin venta 40 + pago CC 60", "saldo 0, venta PARCIAL con 60");
    }
}
