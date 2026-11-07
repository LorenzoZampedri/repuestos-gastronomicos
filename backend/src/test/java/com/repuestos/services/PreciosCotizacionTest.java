package com.repuestos.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repuestos.dto.PedidoCrearRequest;
import com.repuestos.dto.PedidoItemRequest;
import com.repuestos.dto.PedidoResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.LogActividad;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.ConfiguracionRepository;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.ProductoRepository;
import com.repuestos.repositories.UsuarioRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sincronización de precios en pesos al cambiar la cotización USD → ARS:
 * comportamiento nuevo a propósito (antes el precio en pesos quedaba viejo y
 * ventas/pedidos cobraban con él).
 */
@DataJpaTest
@Import({ConfiguracionService.class, AuditoriaService.class, ProductoService.class,
        PreciosMasivosService.class, VentaService.class, PedidoService.class,
        ClienteService.class})
class PreciosCotizacionTest {

    @Autowired private ConfiguracionService configuracionService;
    @Autowired private VentaService ventaService;
    @Autowired private PedidoService pedidoService;
    @Autowired private PreciosMasivosService preciosMasivosService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ConfiguracionRepository configuracionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private LogActividadRepository logActividadRepository;
    @Autowired private EntityManager entityManager;

    private Producto conUsd;
    private Producto sinUsd;
    private Producto inactivo;

    @BeforeEach
    void setUp() {
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .username("admin-cotizacion-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Admin")
                .rol(Usuario.Rol.ADMIN)
                .activo(true)
                .build());
        autenticar(admin);

        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1000", "Cotización");

        conUsd = guardarProducto("Con USD", "10.00", "10000.00", true);
        sinUsd = guardarProducto("Sin USD", null, "500.00", true);
        inactivo = guardarProducto("Inactivo", "10.00", "10000.00", false);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cambiarLaCotizacionRecalculaElArsDeLosProductosActivosConUsd() throws Exception {
        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1200", "Cotización");

        assertEquals(0, new BigDecimal("12000.00").compareTo(buscar(conUsd.getId()).getPrecio()));
        // Sin precio USD: no se toca.
        assertEquals(0, new BigDecimal("500.00").compareTo(buscar(sinUsd.getId()).getPrecio()));
        // Inactivos: no se tocan.
        assertEquals(0, new BigDecimal("10000.00").compareTo(buscar(inactivo.getId()).getPrecio()));

        assertEquals(1, contar(AccionAuditoria.PRECIOS_RECALCULADOS));
        LogActividad log = buscar(AccionAuditoria.PRECIOS_RECALCULADOS);
        assertTrue(log.getResumen().contains("1000"));
        assertTrue(log.getResumen().contains("1200"));
        JsonNode detalle = new ObjectMapper().readTree(log.getDetalle());
        assertEquals(0, new BigDecimal("1000")
                .compareTo(detalle.get("cotizacionAnterior").decimalValue()));
        assertEquals(0, new BigDecimal("1200")
                .compareTo(detalle.get("cotizacionNueva").decimalValue()));
        assertEquals(1, detalle.get("cantidadProductos").asInt());
    }

    @Test
    void reguardarLaMismaCotizacionNoRecalculaNada() {
        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1200", "Cotización");
        assertEquals(1, contar(AccionAuditoria.PRECIOS_RECALCULADOS));

        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1200", "Cotización");

        assertEquals(1, contar(AccionAuditoria.PRECIOS_RECALCULADOS));
        assertEquals(0, new BigDecimal("12000.00").compareTo(buscar(conUsd.getId()).getPrecio()));
    }

    @Test
    void elSembradoInicialDeClavesNoDisparaElRecalculo() {
        configuracionRepository.findByClave(ConfiguracionService.CLAVE_COTIZACION_USD_ARS)
                .ifPresent(configuracionRepository::delete);
        entityManager.flush();

        configuracionService.inicializarClaves();

        assertEquals(0, contar(AccionAuditoria.PRECIOS_RECALCULADOS));
        assertEquals(0, new BigDecimal(ConfiguracionService.COTIZACION_INICIAL)
                .compareTo(configuracionService.getCotizacionUsdArs()));
    }

    @Test
    void migrarPreciosUsdNoDisparaElRecalculo() {
        configuracionService.inicializarClaves();

        configuracionService.migrarPreciosUsd();

        assertEquals(0, contar(AccionAuditoria.PRECIOS_RECALCULADOS));
        assertTrue(buscar(sinUsd.getId()).getPrecioUsd() != null
                && buscar(sinUsd.getId()).getPrecioUsd().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void unaVentaPosteriorAlCambioUsaElArsNuevo() {
        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1200", "Cotización");

        Producto actualizado = buscar(conUsd.getId());
        assertEquals(0, new BigDecimal("12000.00").compareTo(actualizado.getPrecio()));
        assertEquals(0, new BigDecimal("12000.00")
                .compareTo(ventaService.calcularPrecioUnitario(actualizado, 1)));
    }

    @Test
    void laConversionDeUnPedidoPosteriorUsaElArsNuevo() {
        Usuario clienteUsuario = usuarioRepository.save(Usuario.builder()
                .username("cliente-cot-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Cliente Cotización")
                .email("cliente-cot@example.com")
                .rol(Usuario.Rol.CLIENTE)
                .activo(true)
                .build());

        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1200", "Cotización");

        PedidoResponse pedido = pedidoService.crear(clienteUsuario,
                new PedidoCrearRequest(
                        List.of(new PedidoItemRequest(conUsd.getId(), 2)),
                        "11 5555-1234", "Sin sal", "EFECTIVO"));

        assertEquals(0, new BigDecimal("24000.00").compareTo(pedido.totalEstimado()));
    }

    @Test
    void elBotonDeRecalcularCorrigeDesactualizadosYEsIdempotente() {
        Producto p = buscar(conUsd.getId());
        p.setPrecio(new BigDecimal("9999.00"));
        productoRepository.save(p);
        entityManager.flush();

        assertEquals(1, preciosMasivosService.estado().productosDesactualizados());

        assertEquals(1, preciosMasivosService.recalcularArs().cambiados());
        assertEquals(0, new BigDecimal("10000.00").compareTo(buscar(conUsd.getId()).getPrecio()));
        assertEquals(0, preciosMasivosService.recalcularArs().cambiados());
        assertEquals(0, preciosMasivosService.estado().productosDesactualizados());
        assertEquals(1, contar(AccionAuditoria.PRECIOS_RECALCULADOS));
    }

    private Producto guardarProducto(String nombre, String usd, String ars, boolean activo) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre)
                .precioUsd(usd == null ? null : new BigDecimal(usd))
                .precio(new BigDecimal(ars))
                .stockActual(3)
                .stockMinimo(1)
                .activo(activo)
                .build());
    }

    private Producto buscar(Long id) {
        return productoRepository.findById(id).orElseThrow();
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
}
