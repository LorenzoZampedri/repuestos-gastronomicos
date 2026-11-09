package com.repuestos.services;

import com.repuestos.dto.CargarDeudaRequest;
import com.repuestos.dto.CargarDeudaResponse;
import com.repuestos.dto.ClienteNuevoRequest;
import com.repuestos.dto.ComprasClienteResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.CargoCuentaCorriente;
import com.repuestos.models.Cliente;
import com.repuestos.models.LogActividad;
import com.repuestos.models.Pago;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.CargoCuentaCorrienteRepository;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.PagoRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.repositories.VentaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({VentaService.class, ClienteService.class, CargoCuentaCorrienteService.class,
        CuentaCorrienteService.class, ProductoService.class, ConfiguracionService.class,
        AuditoriaService.class, FotoPerfilService.class, AvatarStorageService.class})
class CargaDeudaTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private CuentaCorrienteService cuentaCorrienteService;
    @Autowired private ClienteService clienteService;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private CargoCuentaCorrienteRepository cargoRepository;
    @Autowired private LogActividadRepository logRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private EntityManager entityManager;

    private Usuario admin;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        admin = usuarioRepository.save(Usuario.builder()
                .username("admin-deuda-" + SEQ.incrementAndGet() + "-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Admin Deuda")
                .rol(Usuario.Rol.ADMIN)
                .activo(true)
                .build());
        cliente = clienteRepository.save(Cliente.builder()
                .nombre("Cliente Deuda")
                .telefono("1144556677")
                .email("cliente@example.com")
                .saldoCorriente(new BigDecimal("0.00"))
                .activo(true)
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private CargarDeudaRequest request(Long clienteId, ClienteNuevoRequest nuevo,
                                       String monto, String motivo) {
        return new CargarDeudaRequest(clienteId, nuevo,
                monto == null ? null : new BigDecimal(monto), motivo);
    }

    private CargarDeudaResponse cargar(Long clienteId, String monto, String motivo) {
        return cuentaCorrienteService.cargarDeuda(
                request(clienteId, null, monto, motivo), admin);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private BigDecimal saldoCliente(Long id) {
        entityManager.flush();
        entityManager.clear();
        return clienteRepository.findById(id).orElseThrow().getSaldoCorriente();
    }

    private List<LogActividad> logsDe(AccionAuditoria accion) {
        return logRepository.findAll().stream()
                .filter(log -> accion.name().equals(log.getAccion()))
                .toList();
    }

    private NegocioException errorDe(Runnable accion) {
        return assertThrows(NegocioException.class, accion::run);
    }

    @Test
    void cargaDeudaAClienteExistenteSumaSaldoYDejaUnCargoYUnLog() {
        CargarDeudaResponse respuesta = cargar(cliente.getId(), "1500.50", "Repuestos varios");

        assertEquals(0, new BigDecimal("1500.50").compareTo(saldoCliente(cliente.getId())));
        assertFalse(respuesta.clienteCreado());
        assertNotNull(respuesta.cargo().id());

        flushAndClear();
        List<CargoCuentaCorriente> cargos = cargoRepository.findAll();
        assertEquals(1, cargos.size());
        CargoCuentaCorriente cargo = cargos.get(0);
        assertEquals(0, new BigDecimal("1500.50").compareTo(cargo.getMonto()));
        assertEquals("Repuestos varios", cargo.getMotivo());
        assertEquals(admin.getId(), cargo.getUsuario().getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(cargo.getSaldoAntes()));
        assertEquals(0, new BigDecimal("1500.50").compareTo(cargo.getSaldoDespues()));

        List<LogActividad> logs = logsDe(AccionAuditoria.DEUDA_CARGADA);
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getDetalle().contains("\"cargoId\":" + cargo.getId()));
        assertTrue(logs.get(0).getDetalle().contains("\"clienteCreado\":false"));
    }

    @Test
    void cargaSobreSaldoAFavorTomaElSaldoNegativoComoReferencia() {
        Cliente conFavor = clienteRepository.save(Cliente.builder()
                .nombre("Cliente con favor")
                .saldoCorriente(new BigDecimal("-20.00"))
                .activo(true)
                .build());

        cargar(conFavor.getId(), "50.00", "Compra a cuenta");

        assertEquals(0, new BigDecimal("30.00").compareTo(saldoCliente(conFavor.getId())));
        CargoCuentaCorriente cargo = cargoRepository.findAll().get(0);
        assertEquals(0, new BigDecimal("-20.00").compareTo(cargo.getSaldoAntes()));
        assertEquals(0, new BigDecimal("30.00").compareTo(cargo.getSaldoDespues()));
    }

    @Test
    void clienteNuevoSeCreaConLaDeudaEnLaMismaOperacionYDosLogs() {
        CargarDeudaResponse respuesta = cuentaCorrienteService.cargarDeuda(
                request(null, new ClienteNuevoRequest(
                        "  María   Gómez  ", "11 5555-1234", "MARIA@example.com", null),
                        "800.00", "Deuda inicial"),
                admin);

        assertTrue(respuesta.clienteCreado());
        Cliente creado = clienteRepository.findById(respuesta.cliente().id()).orElseThrow();
        assertEquals("María   Gómez", creado.getNombre());
        assertEquals(0, new BigDecimal("800.00").compareTo(creado.getSaldoCorriente()));
        assertEquals(1, cargoRepository.count());
        assertEquals(respuesta.cliente().id(), cargoRepository.findAll().get(0).getCliente().getId());

        List<LogActividad> creados = logsDe(AccionAuditoria.CLIENTE_CREADO);
        assertEquals(1, creados.size());
        assertTrue(creados.get(0).getDetalle().contains("\"origen\":\"carga de deuda\""));
        assertEquals(1, logsDe(AccionAuditoria.DEUDA_CARGADA).size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void siFallaDespuesDeCrearElClienteNoQuedaNadaPersistido() {
        String nombre = "Cliente Rollback " + System.nanoTime();
        long clientesAntes = clienteRepository.count();

        NegocioException error = errorDe(() -> cuentaCorrienteService.cargarDeuda(
                request(null, new ClienteNuevoRequest(nombre, null, null, null),
                        "100000000.00", "Monto fuera de rango"),
                admin));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(clientesAntes, clienteRepository.count());
        assertEquals(0, clienteRepository.findAll().stream()
                .filter(c -> nombre.equals(c.getNombre())).count());
        assertEquals(0, cargoRepository.count());
    }

    @Test
    void detectaDuplicadosPorNombreTelefonoEmailYCuit() {
        Cliente existente = clienteRepository.save(Cliente.builder()
                .nombre("José   Pérez")
                .telefono("11 4444-9999")
                .email("Jose@Example.com")
                .cuit("20-12345678-9")
                .activo(true)
                .build());

        assertDuplicado(new ClienteNuevoRequest("jose perez", null, null, null),
                existente, "CLIENTE_DUPLICADO");
        assertDuplicado(new ClienteNuevoRequest("Otro Nombre", "1144449999", null, null),
                existente, "CLIENTE_DUPLICADO");
        assertDuplicado(new ClienteNuevoRequest("Otro Nombre", null, "jose@example.COM", null),
                existente, "CLIENTE_DUPLICADO");
        assertDuplicado(new ClienteNuevoRequest("Otro Nombre", null, null, "20123456789"),
                existente, "CLIENTE_DUPLICADO");
        assertEquals(0, cargoRepository.count());
    }

    private void assertDuplicado(ClienteNuevoRequest nuevo, Cliente existente, String codigo) {
        NegocioException error = errorDe(() -> cuentaCorrienteService.cargarDeuda(
                request(null, nuevo, "10.00", "Duplicado"), admin));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals(codigo, error.getCode());
        assertEquals(existente.getId(), error.getDatos().get("clienteId"));
    }

    @Test
    void clienteInactivoSeRechazaTantoPorIdComoPorDuplicado() {
        Cliente inactivo = clienteRepository.save(Cliente.builder()
                .nombre("Cliente Inactivo")
                .telefono("1155550000")
                .activo(false)
                .build());

        NegocioException porId = errorDe(() -> cargar(inactivo.getId(), "10.00", "Deuda"));
        assertEquals(HttpStatus.CONFLICT, porId.getStatus());
        assertEquals("CLIENTE_INACTIVO", porId.getCode());

        NegocioException porDuplicado = errorDe(() -> cuentaCorrienteService.cargarDeuda(
                request(null, new ClienteNuevoRequest("cliente inactivo", null, null, null),
                        "10.00", "Deuda"), admin));
        assertEquals("CLIENTE_INACTIVO", porDuplicado.getCode());
        assertEquals(inactivo.getId(), porDuplicado.getDatos().get("clienteId"));
        assertEquals(0, cargoRepository.count());
    }

    @Test
    void exigeExactamenteClienteExistenteONuevo() {
        NegocioException ninguno = errorDe(() -> cuentaCorrienteService.cargarDeuda(
                request(null, null, "10.00", "Deuda"), admin));
        assertEquals(HttpStatus.BAD_REQUEST, ninguno.getStatus());

        NegocioException ambos = errorDe(() -> cuentaCorrienteService.cargarDeuda(
                request(cliente.getId(), new ClienteNuevoRequest("Nuevo", null, null, null),
                        "10.00", "Deuda"), admin));
        assertEquals(HttpStatus.BAD_REQUEST, ambos.getStatus());
    }

    @Test
    void validaMontoPositivoDecimalesYDesborde() {
        assertErrorMonto("0", HttpStatus.BAD_REQUEST);
        assertErrorMonto("-1.00", HttpStatus.BAD_REQUEST);
        assertErrorMonto(null, HttpStatus.BAD_REQUEST);
        assertErrorMonto("10.123", HttpStatus.BAD_REQUEST);
        assertErrorMonto("100000000.00", HttpStatus.BAD_REQUEST);

        Cliente casiAlMaximo = clienteRepository.save(Cliente.builder()
                .nombre("Cliente al máximo")
                .saldoCorriente(new BigDecimal("99999999.00"))
                .activo(true)
                .build());
        NegocioException desborde = errorDe(() -> cargar(casiAlMaximo.getId(), "1.00", "Deuda"));
        assertEquals(HttpStatus.BAD_REQUEST, desborde.getStatus());
        assertTrue(desborde.getMessage().contains("99.999.999,99"));
    }

    private void assertErrorMonto(String monto, HttpStatus status) {
        NegocioException error = errorDe(() -> cargar(cliente.getId(), monto, "Motivo válido"));
        assertEquals(status, error.getStatus());
    }

    @Test
    void validaMotivoObligatorioYLongitud() {
        assertErrorMotivo(null);
        assertErrorMotivo("   ");
        assertErrorMotivo("ab");
        assertErrorMotivo("a".repeat(301));
        assertEquals(0, cargoRepository.count());
    }

    private void assertErrorMotivo(String motivo) {
        NegocioException error = errorDe(() -> cargar(cliente.getId(), "10.00", motivo));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void rechazaCargoIdenticoRepetidoDentroDeTreintaSegundos() {
        cargar(cliente.getId(), "100.00", "Compra repetida");

        NegocioException error = errorDe(() -> cargar(cliente.getId(), "100.00", "Compra repetida"));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("DEUDA_DUPLICADA", error.getCode());
        assertEquals("Ya cargaste esta misma deuda hace instantes", error.getMessage());
        assertEquals(1, cargoRepository.count());
        assertEquals(0, new BigDecimal("100.00").compareTo(saldoCliente(cliente.getId())));
    }

    @Test
    void permiteMismoMontoConMotivoDistinto() {
        cargar(cliente.getId(), "100.00", "Primer motivo");
        cargar(cliente.getId(), "100.00", "Segundo motivo");

        assertEquals(2, cargoRepository.count());
        assertEquals(0, new BigDecimal("200.00").compareTo(saldoCliente(cliente.getId())));
    }

    @Test
    void sumarDesdePatchGeneraCargoConMotivoPorDefectoYAuditoriaNueva() {
        clienteService.ajustarSaldo(cliente.getId(), new BigDecimal("25.00"),
                "sumar", null, admin);
        flushAndClear();

        List<CargoCuentaCorriente> cargos = cargoRepository.findAll();
        assertEquals(1, cargos.size());
        assertEquals("Ajuste manual de saldo", cargos.get(0).getMotivo());
        assertEquals(1, logsDe(AccionAuditoria.DEUDA_CARGADA).size());
        assertEquals(0, logsDe(AccionAuditoria.CLIENTE_SALDO_AJUSTADO).size());
    }

    @Test
    void deudaManualYPagoPosteriorConvivvenConElRemanenteSinVenta() {
        cargar(cliente.getId(), "10000.00", "Deuda manual");

        clienteService.ajustarSaldo(cliente.getId(), new BigDecimal("4000.00"),
                "restar", "EFECTIVO", admin);
        flushAndClear();

        assertEquals(0, new BigDecimal("6000.00").compareTo(saldoCliente(cliente.getId())));
        List<Pago> pagos = pagoRepository.findPagosPorCliente(cliente.getId());
        assertEquals(1, pagos.size());
        assertNull(pagos.get(0).getVenta());
        assertEquals(0, new BigDecimal("4000.00").compareTo(pagos.get(0).getMonto()));
        assertEquals(1, logsDe(AccionAuditoria.CLIENTE_SALDO_AJUSTADO).size());
    }

    @Test
    void comprasAdminSeAgrupanPorEstadoConConteosLimiteYOrden() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0);
        venta("100.00", Venta.EstadoVenta.PAGADA, base.plusDays(1));
        venta("200.00", Venta.EstadoVenta.PENDIENTE, base.plusDays(3));
        venta("300.00", Venta.EstadoVenta.PARCIAL, base.plusDays(2));
        venta("400.00", Venta.EstadoVenta.ANULADA, base.plusDays(4));
        flushAndClear();

        ComprasClienteResponse compras = cuentaCorrienteService.comprasDeCliente(
                cliente.getId(), 50, true);

        assertEquals(1, compras.realizadas().cantidad());
        assertEquals(2, compras.pendientes().cantidad());
        assertEquals(1, compras.canceladas().cantidad());
        assertEquals(Venta.EstadoVenta.PENDIENTE.name(),
                compras.pendientes().items().get(0).estado());
        assertEquals(Venta.EstadoVenta.PARCIAL.name(),
                compras.pendientes().items().get(1).estado());
        assertNotNull(compras.realizadas().items().get(0).saldoPendiente());

        ComprasClienteResponse soloConteos = cuentaCorrienteService.comprasDeCliente(
                cliente.getId(), 0, true);
        assertEquals(2, soloConteos.pendientes().cantidad());
        assertTrue(soloConteos.pendientes().items().isEmpty());

        ComprasClienteResponse limiteUno = cuentaCorrienteService.comprasDeCliente(
                cliente.getId(), 1, true);
        assertEquals(2, limiteUno.pendientes().cantidad());
        assertEquals(1, limiteUno.pendientes().items().size());
    }

    @Test
    void comprasEmpleadoSoloDevuelveRealizadasSinSaldos() {
        venta("100.00", Venta.EstadoVenta.PAGADA, LocalDateTime.now());
        venta("200.00", Venta.EstadoVenta.PENDIENTE, LocalDateTime.now());
        venta("300.00", Venta.EstadoVenta.ANULADA, LocalDateTime.now());
        flushAndClear();

        ComprasClienteResponse compras = cuentaCorrienteService.comprasDeCliente(
                cliente.getId(), 50, false);

        assertEquals(1, compras.realizadas().cantidad());
        assertNull(compras.pendientes());
        assertNull(compras.canceladas());
        assertNull(compras.realizadas().items().get(0).saldoPendiente());
    }

    @Test
    void comprasDeClienteInexistenteDevuelveNotFound() {
        NegocioException error = errorDe(() -> cuentaCorrienteService.comprasDeCliente(
                999999L, 20, true));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    private Venta venta(String total, Venta.EstadoVenta estado, LocalDateTime fecha) {
        return ventaRepository.save(Venta.builder()
                .usuario(admin)
                .cliente(cliente)
                .total(new BigDecimal(total))
                .estado(estado)
                .fecha(fecha)
                .build());
    }
}
