package com.repuestos.services;

import com.repuestos.models.Cliente;
import com.repuestos.models.Pago;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.PagoRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({VentaService.class, ClienteService.class, ProductoService.class, ConfiguracionService.class, AuditoriaService.class})
class RegistrarPagoTest {

    @Autowired private VentaService ventaService;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
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
                .nombre("Cliente Test")
                .saldoCorriente(new BigDecimal("100.00"))
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

    private Pago pago(String monto) {
        return Pago.builder()
                .monto(new BigDecimal(monto))
                .metodoPago(Pago.MetodoPago.EFECTIVO)
                .build();
    }

    // Emula un request HTTP nuevo: EntityManager nuevo, entidades sin inicializar.
    private void registrarPagoComoRequest(Long ventaId, String monto) {
        entityManager.clear();
        ventaService.registrarPago(ventaId, pago(monto));
        entityManager.flush(); // simula el commit del request
    }

    private BigDecimal montoPagadoReal(Long ventaId) {
        return pagoRepository.findByVentaId(ventaId).stream()
                .map(Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal saldoCliente() {
        entityManager.clear();
        return clienteRepository.findById(cliente.getId()).orElseThrow().getSaldoCorriente();
    }

    private Venta recargarVenta(Long id) {
        entityManager.clear();
        return ventaRepository.findById(id).orElseThrow();
    }

    // ---------------------------------------------------------------------
    // PASO 0: reproducción del posible doble conteo en registrarPago
    // ---------------------------------------------------------------------

    @Test
    void pagoParcialSobreVentaDe100DebeDejarMontoPagado40() {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));

        registrarPagoComoRequest(venta.getId(), "40.00");

        Venta recargada = recargarVenta(venta.getId());
        assertEquals(Venta.EstadoVenta.PARCIAL, recargada.getEstado(),
                "estado tras pago parcial, montoPagado real=" + montoPagadoReal(venta.getId()));
        assertEquals(0, new BigDecimal("40.00").compareTo(montoPagadoReal(venta.getId())),
                "los pagos registrados en DB deben sumar 40.00");
        assertEquals(0, new BigDecimal("40.00").compareTo(recargada.getMontoPagado()),
                "getMontoPagado() debe devolver 40.00 pero devolvio " + recargada.getMontoPagado());
        assertEquals(0, new BigDecimal("60.00").compareTo(recargada.getSaldoPendiente()),
                "saldo pendiente debe ser 60.00 pero devolvio " + recargada.getSaldoPendiente());
        assertEquals(0, new BigDecimal("60.00").compareTo(saldoCliente()),
                "el saldoCorriente del cliente debe bajar exactamente 40.00");
    }

    @Test
    void pagoDe60SobreVentaDe100NoDebeMarcarlaPagada() {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));

        registrarPagoComoRequest(venta.getId(), "60.00");

        Venta recargada = recargarVenta(venta.getId());
        assertEquals(Venta.EstadoVenta.PARCIAL, recargada.getEstado(),
                "un pago de 60 sobre total 100 no puede marcar la venta PAGADA");
        assertEquals(0, new BigDecimal("60.00").compareTo(recargada.getMontoPagado()),
                "getMontoPagado() debe devolver 60.00 pero devolvio " + recargada.getMontoPagado());
        assertEquals(0, new BigDecimal("40.00").compareTo(recargada.getSaldoPendiente()));
        assertEquals(0, new BigDecimal("40.00").compareTo(saldoCliente()));
    }

    // ---------------------------------------------------------------------
    // Escenarios de pago sobre una venta
    // ---------------------------------------------------------------------

    @Test
    void pagoQueCompletaLaVentaQuedaPagada() {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));

        registrarPagoComoRequest(venta.getId(), "40.00");
        registrarPagoComoRequest(venta.getId(), "60.00");

        Venta recargada = recargarVenta(venta.getId());
        assertEquals(Venta.EstadoVenta.PAGADA, recargada.getEstado());
        assertEquals(0, new BigDecimal("100.00").compareTo(montoPagadoReal(venta.getId())));
        assertEquals(0, new BigDecimal("100.00").compareTo(recargada.getMontoPagado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(recargada.getSaldoPendiente()));
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoCliente()));
    }

    @Test
    void dosPagosDe30DejanParcialCon60() {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));

        registrarPagoComoRequest(venta.getId(), "30.00");
        registrarPagoComoRequest(venta.getId(), "30.00");

        Venta recargada = recargarVenta(venta.getId());
        assertEquals(Venta.EstadoVenta.PARCIAL, recargada.getEstado());
        assertEquals(0, new BigDecimal("60.00").compareTo(montoPagadoReal(venta.getId())));
        assertEquals(0, new BigDecimal("60.00").compareTo(recargada.getMontoPagado()));
        assertEquals(0, new BigDecimal("40.00").compareTo(recargada.getSaldoPendiente()));
        assertEquals(0, new BigDecimal("40.00").compareTo(saldoCliente()));
    }

    // ---------------------------------------------------------------------
    // Validaciones de registrarPago
    // ---------------------------------------------------------------------

    @Test
    void pagoQueSuperaElSaldoPendienteRechazaYSinCambios() {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));

        entityManager.clear();
        assertThrows(IllegalArgumentException.class,
                () -> ventaService.registrarPago(venta.getId(), pago("150.00")));

        Venta recargada = recargarVenta(venta.getId());
        assertEquals(Venta.EstadoVenta.PENDIENTE, recargada.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(montoPagadoReal(venta.getId())));
        assertEquals(0, BigDecimal.ZERO.compareTo(recargada.getMontoPagado()));
        assertEquals(0, new BigDecimal("100.00").compareTo(saldoCliente()));
    }

    @Test
    void pagoSobreVentaAnuladaRechazaYSinCambios() {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        entityManager.clear();
        ventaService.anularVenta(venta.getId());
        entityManager.flush();

        entityManager.clear();
        assertThrows(IllegalArgumentException.class,
                () -> ventaService.registrarPago(venta.getId(), pago("50.00")));

        Venta recargada = recargarVenta(venta.getId());
        assertEquals(Venta.EstadoVenta.ANULADA, recargada.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(montoPagadoReal(venta.getId())));
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoCliente()),
                "al anular una venta impaga se debe cancelar la deuda del cliente");
    }

    @Test
    void pagoCeroONegativoRechazado() {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        entityManager.clear();
        assertThrows(IllegalArgumentException.class,
                () -> ventaService.registrarPago(venta.getId(), pago("0")));
        assertThrows(IllegalArgumentException.class,
                () -> ventaService.registrarPago(venta.getId(), pago("-10")));
    }

    @Test
    void pagoSobreVentaPagadaRechazado() {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        registrarPagoComoRequest(venta.getId(), "100.00");

        entityManager.clear();
        assertThrows(IllegalArgumentException.class,
                () -> ventaService.registrarPago(venta.getId(), pago("10.00")));

        assertEquals(0, new BigDecimal("100.00").compareTo(montoPagadoReal(venta.getId())),
                "no debe registrarse un pago extra sobre una venta pagada");
    }

    @Test
    void ventaExponeMontoPagadoYSaldoPendienteEnJson() throws Exception {
        Venta venta = crearVenta("100.00", LocalDateTime.of(2026, 1, 1, 10, 0));
        registrarPagoComoRequest(venta.getId(), "40.00");

        Venta recargada = recargarVenta(venta.getId());
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(recargada);

        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"montoPagado\":40.00"),
                "el JSON debe incluir montoPagado=40.00 pero fue: " + json);
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"saldoPendiente\":60.00"),
                "el JSON debe incluir saldoPendiente=60.00 pero fue: " + json);
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"estado\":\"PARCIAL\""),
                "el JSON debe incluir estado=PARCIAL pero fue: " + json);
    }

}
