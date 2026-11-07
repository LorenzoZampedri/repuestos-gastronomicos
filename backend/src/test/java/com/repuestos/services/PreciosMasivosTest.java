package com.repuestos.services;

import com.repuestos.dto.LotePreciosDetalleResponse;
import com.repuestos.dto.PaginaLotesResponse;
import com.repuestos.dto.PrecioExcluido;
import com.repuestos.dto.PreciosAjusteRequest;
import com.repuestos.dto.PreciosAplicarResponse;
import com.repuestos.dto.PreciosEstadoResponse;
import com.repuestos.dto.PreciosRecalcularResponse;
import com.repuestos.dto.PreciosRevertirResponse;
import com.repuestos.dto.PreciosVistaPreviaResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Categoria;
import com.repuestos.models.Cliente;
import com.repuestos.models.DetallePedido;
import com.repuestos.models.LotePrecios;
import com.repuestos.models.LotePreciosItem;
import com.repuestos.models.LogActividad;
import com.repuestos.models.Pedido;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.CategoriaRepository;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.LotePreciosItemRepository;
import com.repuestos.repositories.LotePreciosRepository;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.PedidoRepository;
import com.repuestos.repositories.ProductoRepository;
import com.repuestos.repositories.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({PreciosMasivosService.class, ConfiguracionService.class, AuditoriaService.class})
class PreciosMasivosTest {

    @Autowired private PreciosMasivosService preciosMasivosService;
    @Autowired private ConfiguracionService configuracionService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private LotePreciosRepository lotePreciosRepository;
    @Autowired private LotePreciosItemRepository lotePreciosItemRepository;
    @Autowired private LogActividadRepository logActividadRepository;
    @Autowired private EntityManager entityManager;

    private Categoria gas;
    private Categoria electrico;
    private Producto p1;
    private Producto p2;
    private Producto p3;
    private Producto sinUsd;
    private Producto inactivo;

    @BeforeEach
    void setUp() {
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .username("admin-precios-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Admin Precios")
                .rol(Usuario.Rol.ADMIN)
                .activo(true)
                .build());
        autenticar(admin);

        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1000", "Cotización");

        gas = categoriaRepository.save(Categoria.builder().nombre("Gas").build());
        electrico = categoriaRepository.save(Categoria.builder().nombre("Eléctrico").build());

        p1 = guardarProducto("Quemador", "10.00", "10000.00", gas, true);
        p2 = guardarProducto("Freidora", "20.00", "20000.00", gas, true);
        p3 = guardarProducto("Anafe", "5.00", "5000.00", electrico, true);
        sinUsd = guardarProducto("Sin USD", null, "100.00", gas, true);
        inactivo = guardarProducto("Borrado", "10.00", "10000.00", gas, false);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Cálculo y vista previa
    // ------------------------------------------------------------------

    @Test
    void vistaPreviaDeAumentoPorcentualListaAntesYDespuesSinTocarLaBase() {
        PreciosAjusteRequest request = request("PORCENTAJE", "10", "0.01",
                "CATEGORIAS", List.of(gas.getId()), null);

        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(request);

        assertNotNull(preview.token());
        assertEquals(0, new BigDecimal("1000").compareTo(preview.cotizacion()));
        assertEquals(2, preview.resumen().productosAfectados());
        assertEquals(1, preview.resumen().productosExcluidos());
        assertEquals("Categorías: Gas", preview.parametros().alcanceDescripcion());

        var item1 = preview.items().stream()
                .filter(item -> item.productoId().equals(p1.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10.00").compareTo(item1.precioUsdAntes()));
        assertEquals(0, new BigDecimal("11.00").compareTo(item1.precioUsdDespues()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(item1.precioArsAntes()));
        assertEquals(0, new BigDecimal("11000.00").compareTo(item1.precioArsDespues()));
        assertEquals(0, new BigDecimal("10.00").compareTo(item1.variacionPorcentaje()));

        PrecioExcluido excluido = preview.excluidos().get(0);
        assertEquals(sinUsd.getId(), excluido.productoId());
        assertEquals("sin precio en dólares", excluido.motivo());

        // La vista previa no modifica nada.
        assertEquals(0, new BigDecimal("10.00").compareTo(buscar(p1.getId()).getPrecioUsd()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(buscar(p1.getId()).getPrecio()));
        assertEquals(0, lotePreciosRepository.count());
    }

    @Test
    void porcentajeNegativoYMontosPositivoYNegativoCalculanCorrecto() {
        var bajada = preciosMasivosService.vistaPrevia(
                request("PORCENTAJE", "-10", "0.01", "PRODUCTOS", null, List.of(p1.getId())));
        assertEquals(0, new BigDecimal("9.00")
                .compareTo(bajada.items().get(0).precioUsdDespues()));

        var subida = preciosMasivosService.vistaPrevia(
                request("MONTO_USD", "2.50", "0.01", "PRODUCTOS", null, List.of(p1.getId())));
        assertEquals(0, new BigDecimal("12.50")
                .compareTo(subida.items().get(0).precioUsdDespues()));

        var bajaMonto = preciosMasivosService.vistaPrevia(
                request("MONTO_USD", "-3", "0.01", "PRODUCTOS", null, List.of(p1.getId())));
        assertEquals(0, new BigDecimal("7.00")
                .compareTo(bajaMonto.items().get(0).precioUsdDespues()));
    }

    @Test
    void cadaRedondeoUsaHalfUpAlMultiploMasCercano() {
        assertEquals("10.01", nuevoUsd("10.00", "0.005", "0.01"));
        assertEquals("10.10", nuevoUsd("10.03", "0.04", "0.10"));
        assertEquals("10.00", nuevoUsd("10.03", "-0.04", "0.10"));
        assertEquals("10.50", nuevoUsd("10.00", "0.30", "0.50"));
        assertEquals("11.00", nuevoUsd("10.00", "0.50", "1.00"));
        assertEquals("10.00", nuevoUsd("10.49", "-0.10", "1.00"));
    }

    @Test
    void arsDerivadoEsUsdNuevoPorCotizacionConDosDecimalesHalfUp() {
        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1037.5", "Cotización");

        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(
                request("MONTO_USD", "0.33", "0.01", "PRODUCTOS", null, List.of(p1.getId())));

        var item = preview.items().get(0);
        assertEquals(0, new BigDecimal("10.33").compareTo(item.precioUsdDespues()));
        // 10.33 × 1037.5 = 10717.375 → 10717.38 (HALF_UP)
        assertEquals(0, new BigDecimal("10717.38").compareTo(item.precioArsDespues()));
    }

    // ------------------------------------------------------------------
    // Protecciones
    // ------------------------------------------------------------------

    @Test
    void porcentajeFueraDeRangoDevuelve400() {
        NegocioException mayor = assertThrows(NegocioException.class, () ->
                preciosMasivosService.vistaPrevia(
                        request("PORCENTAJE", "150", "0.01", "TODOS", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, mayor.getStatus());

        NegocioException menor = assertThrows(NegocioException.class, () ->
                preciosMasivosService.vistaPrevia(
                        request("PORCENTAJE", "-95", "0.01", "TODOS", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, menor.getStatus());
    }

    @Test
    void variacionAltaExigeElFlagDeConfirmacion() {
        PreciosAjusteRequest request = request("PORCENTAJE", "30", "0.01", "TODOS", null, null);
        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(request);
        assertTrue(preview.resumen().variacionAlta());

        PreciosAjusteRequest sinFlag = conToken(request, preview.token(), null);
        NegocioException rechazado = assertThrows(NegocioException.class,
                () -> preciosMasivosService.aplicar(sinFlag));
        assertEquals(HttpStatus.BAD_REQUEST, rechazado.getStatus());
        assertEquals(0, lotePreciosRepository.count());

        PreciosAplicarResponse aplicado = preciosMasivosService.aplicar(
                conToken(request, preview.token(), true));
        assertNotNull(aplicado.loteId());
    }

    @Test
    void variacionNormalNoPideConfirmacion() {
        PreciosAjusteRequest request = request("PORCENTAJE", "10", "0.01", "TODOS", null, null);
        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(request);
        assertFalse(preview.resumen().variacionAlta());

        PreciosAplicarResponse aplicado = preciosMasivosService.aplicar(
                conToken(request, preview.token(), null));
        assertNotNull(aplicado.loteId());
    }

    @Test
    void productoConResultadoInvalidoQuedaExcluido() {
        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(
                request("MONTO_USD", "-15", "0.01", "PRODUCTOS", null,
                        List.of(p1.getId(), p2.getId())));

        assertEquals(1, preview.resumen().productosAfectados());
        assertEquals(1, preview.resumen().productosExcluidos());
        PrecioExcluido excluido = preview.excluidos().get(0);
        assertEquals(p1.getId(), excluido.productoId());
        assertEquals("resultado inválido", excluido.motivo());
        assertEquals(0, new BigDecimal("10.00").compareTo(buscar(p1.getId()).getPrecioUsd()));
    }

    @Test
    void productoCuyoArsExcedeElMaximoQuedaExcluido() {
        // 99999 USD × 1000 = 99.999.000 ARS (guardable); con +1% el ARS derivado
        // supera el máximo de la columna y el producto queda excluido.
        Producto caro = guardarProducto("Caro", "99999.00", "99999000.00", gas, true);

        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(
                request("PORCENTAJE", "1", "0.01", "PRODUCTOS", null,
                        List.of(caro.getId(), p1.getId())));

        assertEquals(1, preview.resumen().productosAfectados());
        assertEquals(1, preview.resumen().productosExcluidos());
        PrecioExcluido excluido = preview.excluidos().get(0);
        assertEquals(caro.getId(), excluido.productoId());
        assertEquals("excede el máximo", excluido.motivo());
        assertEquals(0, new BigDecimal("99999.00")
                .compareTo(buscar(caro.getId()).getPrecioUsd()));
    }

    @Test
    void ajusteQueNoModificaNingunProductoDevuelve400() {
        NegocioException error = assertThrows(NegocioException.class, () ->
                preciosMasivosService.vistaPrevia(
                        request("PORCENTAJE", "10", "0.01", "PRODUCTOS", null,
                                List.of(sinUsd.getId()))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("El ajuste no modifica ningún producto", error.getMessage());

        NegocioException cero = assertThrows(NegocioException.class, () ->
                preciosMasivosService.vistaPrevia(
                        request("PORCENTAJE", "0", "0.01", "TODOS", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, cero.getStatus());
    }

    @Test
    void redondeoInvalidoYAlcanceInvalidoDevuelven400() {
        NegocioException redondeo = assertThrows(NegocioException.class, () ->
                preciosMasivosService.vistaPrevia(
                        request("PORCENTAJE", "10", "0.07", "TODOS", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, redondeo.getStatus());

        NegocioException alcance = assertThrows(NegocioException.class, () ->
                preciosMasivosService.vistaPrevia(
                        request("PORCENTAJE", "10", "0.01", "CATEGORIAS", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, alcance.getStatus());
    }

    // ------------------------------------------------------------------
    // Alcance
    // ------------------------------------------------------------------

    @Test
    void alcanceTodosSoloTomaProductosActivos() {
        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(
                request("PORCENTAJE", "10", "0.01", "TODOS", null, null));

        assertEquals(3, preview.resumen().productosAfectados());
        assertTrue(preview.items().stream()
                .noneMatch(item -> item.productoId().equals(inactivo.getId())));
        assertTrue(preview.excluidos().stream()
                .noneMatch(item -> item.productoId().equals(inactivo.getId())));

        preciosMasivosService.aplicar(conToken(
                request("PORCENTAJE", "10", "0.01", "TODOS", null, null), preview.token(), null));

        assertEquals(0, new BigDecimal("10.00").compareTo(buscar(inactivo.getId()).getPrecioUsd()));
        assertEquals(0, new BigDecimal("100.00").compareTo(buscar(sinUsd.getId()).getPrecio()));
    }

    @Test
    void alcancePorCategoriasYPorProductos() {
        PreciosVistaPreviaResponse porCategoria = preciosMasivosService.vistaPrevia(
                request("PORCENTAJE", "10", "0.01", "CATEGORIAS",
                        List.of(electrico.getId()), null));
        assertEquals(1, porCategoria.resumen().productosAfectados());
        assertEquals(p3.getId(), porCategoria.items().get(0).productoId());

        PreciosVistaPreviaResponse porProductos = preciosMasivosService.vistaPrevia(
                request("PORCENTAJE", "10", "0.01", "PRODUCTOS", null,
                        List.of(p1.getId(), inactivo.getId(), sinUsd.getId())));
        assertEquals(1, porProductos.resumen().productosAfectados());
        assertEquals(p1.getId(), porProductos.items().get(0).productoId());
        assertEquals(1, porProductos.resumen().productosExcluidos());
    }

    // ------------------------------------------------------------------
    // Token
    // ------------------------------------------------------------------

    @Test
    void aplicarFallaCon409SiElPrecioCambioDesdeLaVistaPrevia() {
        PreciosAjusteRequest request = request("PORCENTAJE", "10", "0.01",
                "CATEGORIAS", List.of(gas.getId()), null);
        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(request);

        Producto p = buscar(p1.getId());
        p.setPrecioUsd(new BigDecimal("12.00"));
        productoRepository.save(p);
        entityManager.flush();

        NegocioException error = assertThrows(NegocioException.class, () ->
                preciosMasivosService.aplicar(conToken(request, preview.token(), null)));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("Los precios cambiaron desde la vista previa: generala de nuevo",
                error.getMessage());
        assertEquals(0, lotePreciosRepository.count());
        assertEquals(0, new BigDecimal("12.00").compareTo(buscar(p1.getId()).getPrecioUsd()));
    }

    @Test
    void aplicarFallaCon409SiLaCotizacionCambioDesdeLaVistaPrevia() {
        PreciosAjusteRequest request = request("PORCENTAJE", "10", "0.01", "TODOS", null, null);
        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(request);

        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1100", "Cotización");

        NegocioException error = assertThrows(NegocioException.class, () ->
                preciosMasivosService.aplicar(conToken(request, preview.token(), null)));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals(0, lotePreciosRepository.count());
    }

    @Test
    void aplicarSinVistaPreviaDevuelve400() {
        NegocioException error = assertThrows(NegocioException.class, () ->
                preciosMasivosService.aplicar(
                        request("PORCENTAJE", "10", "0.01", "TODOS", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("Generá la vista previa antes de aplicar", error.getMessage());
    }

    // ------------------------------------------------------------------
    // Aplicar
    // ------------------------------------------------------------------

    @Test
    void aplicarModificaSoloLosAfectadosYCreaLoteConSnapshots() {
        PreciosAjusteRequest request = request("PORCENTAJE", "10", "0.01",
                "CATEGORIAS", List.of(gas.getId()), null);
        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(request);

        PreciosAplicarResponse respuesta = preciosMasivosService.aplicar(
                conToken(request, preview.token(), null));

        assertEquals(2, respuesta.resumen().productosAfectados());
        assertEquals(1, respuesta.resumen().productosExcluidos());

        assertEquals(0, new BigDecimal("11.00").compareTo(buscar(p1.getId()).getPrecioUsd()));
        assertEquals(0, new BigDecimal("11000.00").compareTo(buscar(p1.getId()).getPrecio()));
        assertEquals(0, new BigDecimal("22.00").compareTo(buscar(p2.getId()).getPrecioUsd()));
        assertEquals(0, new BigDecimal("22000.00").compareTo(buscar(p2.getId()).getPrecio()));
        // Ajenos al alcance: intactos.
        assertEquals(0, new BigDecimal("5.00").compareTo(buscar(p3.getId()).getPrecioUsd()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(buscar(p3.getId()).getPrecio()));
        assertNullPrecioUsdDe(sinUsd);
        assertEquals(0, new BigDecimal("10.00").compareTo(buscar(inactivo.getId()).getPrecioUsd()));

        LotePrecios lote = lotePreciosRepository.findById(respuesta.loteId()).orElseThrow();
        assertEquals(LotePrecios.ESTADO_APLICADO, lote.getEstado());
        assertEquals("Categorías: Gas", lote.getAlcance());
        assertEquals(2, lote.getCantidadProductos());
        assertEquals(1, lote.getCantidadOmitidos());
        assertEquals(0, new BigDecimal("1000").compareTo(lote.getCotizacionUsada()));
        assertNotNull(lote.getUsuarioUsername());
        assertTrue(lote.getUsuarioUsername().startsWith("admin-precios-"));

        List<LotePreciosItem> items = lotePreciosItemRepository
                .findByLoteIdOrderByIdAsc(lote.getId());
        assertEquals(2, items.size());
        LotePreciosItem item1 = items.stream()
                .filter(item -> item.getProductoId().equals(p1.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10.00").compareTo(item1.getPrecioUsdAntes()));
        assertEquals(0, new BigDecimal("11.00").compareTo(item1.getPrecioUsdDespues()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(item1.getPrecioArsAntes()));
        assertEquals(0, new BigDecimal("11000.00").compareTo(item1.getPrecioArsDespues()));
        assertEquals("Quemador", item1.getProductoNombre());

        assertEquals(1, contar(AccionAuditoria.PRECIOS_ACTUALIZADOS_MASIVO));
        assertEquals(0, contar(AccionAuditoria.PRODUCTO_EDITADO));
        LogActividad log = buscar(AccionAuditoria.PRECIOS_ACTUALIZADOS_MASIVO);
        assertEquals(lote.getId(), log.getEntidadId());
        assertTrue(log.getResumen().contains("lote #" + lote.getId()));
    }

    // ------------------------------------------------------------------
    // Revertir
    // ------------------------------------------------------------------

    @Test
    void revertirRestauraPreciosYOmiteLosEditadosDespues() {
        PreciosAjusteRequest request = request("PORCENTAJE", "10", "0.01",
                "CATEGORIAS", List.of(gas.getId()), null);
        Long loteId = preciosMasivosService.aplicar(conToken(request,
                preciosMasivosService.vistaPrevia(request).token(), null)).loteId();

        // Alguien edita p1 a mano después del lote.
        Producto p = buscar(p1.getId());
        p.setPrecioUsd(new BigDecimal("12.50"));
        p.setPrecio(new BigDecimal("12500.00"));
        productoRepository.save(p);
        entityManager.flush();

        PreciosRevertirResponse respuesta = preciosMasivosService.revertir(loteId);

        assertEquals(1, respuesta.revertidos());
        assertEquals(1, respuesta.omitidos().size());
        assertEquals(p1.getId(), respuesta.omitidos().get(0).productoId());
        assertEquals("el precio fue editado después del lote",
                respuesta.omitidos().get(0).motivo());

        // p1 sigue como lo dejó la edición manual.
        assertEquals(0, new BigDecimal("12.50").compareTo(buscar(p1.getId()).getPrecioUsd()));
        // p2 vuelve al precio anterior, con ARS recalculado.
        assertEquals(0, new BigDecimal("20.00").compareTo(buscar(p2.getId()).getPrecioUsd()));
        assertEquals(0, new BigDecimal("20000.00").compareTo(buscar(p2.getId()).getPrecio()));

        LotePrecios lote = lotePreciosRepository.findById(loteId).orElseThrow();
        assertEquals(LotePrecios.ESTADO_REVERTIDO, lote.getEstado());
        assertNotNull(lote.getFechaReversion());
        assertNotNull(lote.getRevertidoPorUsername());

        assertEquals(1, contar(AccionAuditoria.PRECIOS_REVERTIDOS));
        assertEquals(1, contar(AccionAuditoria.PRECIOS_ACTUALIZADOS_MASIVO));
    }

    @Test
    void unLoteRevertidoNoSePuedeRevertirOtraVez() {
        PreciosAjusteRequest request = request("PORCENTAJE", "10", "0.01", "TODOS", null, null);
        Long loteId = preciosMasivosService.aplicar(conToken(request,
                preciosMasivosService.vistaPrevia(request).token(), null)).loteId();

        preciosMasivosService.revertir(loteId);

        NegocioException error = assertThrows(NegocioException.class,
                () -> preciosMasivosService.revertir(loteId));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("Este lote ya fue revertido", error.getMessage());
        assertEquals(1, contar(AccionAuditoria.PRECIOS_REVERTIDOS));
    }

    // ------------------------------------------------------------------
    // Historial
    // ------------------------------------------------------------------

    @Test
    void historialPaginaLotesYDevuelveElDetalle() {
        Long primero = aplicarLoteSimple("10");
        Long segundo = aplicarLoteSimple("5");

        PaginaLotesResponse pagina = preciosMasivosService.listarLotes(0, 1);
        assertEquals(2, pagina.totalElements());
        assertEquals(2, pagina.totalPages());
        assertEquals(1, pagina.content().size());
        assertEquals(segundo, pagina.content().get(0).id());

        LotePreciosDetalleResponse detalle = preciosMasivosService.detalleLote(primero);
        assertEquals(primero, detalle.lote().id());
        assertFalse(detalle.items().isEmpty());
        assertEquals(0, new BigDecimal("5.00").compareTo(detalle.items().get(0).precioUsdAntes()));
        assertEquals(0, new BigDecimal("5.50").compareTo(detalle.items().get(0).precioUsdDespues()));

        NegocioException noExiste = assertThrows(NegocioException.class,
                () -> preciosMasivosService.detalleLote(999999L));
        assertEquals(HttpStatus.NOT_FOUND, noExiste.getStatus());
    }

    // ------------------------------------------------------------------
    // Estado y recálculo
    // ------------------------------------------------------------------

    @Test
    void estadoCuentaDesactualizadosYSinPrecioUsd() {
        assertEquals(0, new BigDecimal("1000")
                .compareTo(preciosMasivosService.estado().cotizacion()));

        PreciosEstadoResponse inicial = preciosMasivosService.estado();
        assertEquals(0, inicial.productosDesactualizados());
        assertEquals(1, inicial.productosSinPrecioUsd());

        Producto p = buscar(p2.getId());
        p.setPrecio(new BigDecimal("19999.00"));
        productoRepository.save(p);
        entityManager.flush();

        PreciosEstadoResponse conDesactualizado = preciosMasivosService.estado();
        assertEquals(1, conDesactualizado.productosDesactualizados());
        assertEquals(1, conDesactualizado.productosSinPrecioUsd());
    }

    @Test
    void recalcularArsCorrigeLosPreciosYEsIdempotente() {
        Producto p = buscar(p2.getId());
        p.setPrecio(new BigDecimal("19999.00"));
        productoRepository.save(p);
        entityManager.flush();

        PreciosRecalcularResponse primera = preciosMasivosService.recalcularArs();
        assertEquals(1, primera.cambiados());
        assertEquals(0, new BigDecimal("20000.00").compareTo(buscar(p2.getId()).getPrecio()));
        assertEquals(1, contar(AccionAuditoria.PRECIOS_RECALCULADOS));

        PreciosRecalcularResponse segunda = preciosMasivosService.recalcularArs();
        assertEquals(0, segunda.cambiados());
        assertEquals(1, contar(AccionAuditoria.PRECIOS_RECALCULADOS));
    }

    @Test
    void vistaPreviaInformaPedidosPendientesAfectados() {
        Cliente cliente = clienteRepository.save(Cliente.builder()
                .nombre("Cliente Precios").saldoCorriente(BigDecimal.ZERO).activo(true).build());
        Usuario clienteUsuario = usuarioRepository.save(Usuario.builder()
                .username("cliente-precios-" + System.nanoTime())
                .passwordHash("hash").nombre("Cliente")
                .rol(Usuario.Rol.CLIENTE).activo(true).build());
        Pedido pedido = Pedido.builder()
                .cliente(cliente)
                .usuario(clienteUsuario)
                .fecha(java.time.LocalDateTime.now())
                .estado(Pedido.EstadoPedido.PENDIENTE)
                .build();
        pedido.agregarDetalle(DetallePedido.builder()
                .producto(p1).cantidad(1).nombreProducto(p1.getNombre())
                .precioUnitarioEstimado(new BigDecimal("10000.00")).build());
        pedidoRepository.save(pedido);

        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(
                request("PORCENTAJE", "10", "0.01", "CATEGORIAS", List.of(gas.getId()), null));

        assertEquals(1, preview.pedidosPendientesAfectados());
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private String nuevoUsd(String usdActual, String monto, String redondeo) {
        Producto producto = guardarProducto("R" + System.nanoTime(), usdActual,
                new BigDecimal(usdActual).multiply(new BigDecimal("1000")).toPlainString(),
                gas, true);
        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(
                request("MONTO_USD", monto, redondeo, "PRODUCTOS", null,
                        List.of(producto.getId())));
        assertEquals(1, preview.resumen().productosAfectados(),
                "El caso de redondeo debe modificar el precio para poder observarlo");
        return preview.items().get(0).precioUsdDespues().toPlainString();
    }

    private Long aplicarLoteSimple(String porcentaje) {
        PreciosAjusteRequest request = request("PORCENTAJE", porcentaje, "0.01",
                "CATEGORIAS", List.of(electrico.getId()), null);
        return preciosMasivosService.aplicar(conToken(request,
                preciosMasivosService.vistaPrevia(request).token(), null)).loteId();
    }

    private void assertNullPrecioUsdDe(Producto producto) {
        Producto actual = buscar(producto.getId());
        assertTrue(actual.getPrecioUsd() == null
                || actual.getPrecioUsd().compareTo(BigDecimal.ZERO) <= 0);
    }

    private Producto buscar(Long id) {
        return productoRepository.findById(id).orElseThrow();
    }

    private Producto guardarProducto(String nombre, String usd, String ars,
                                     Categoria categoria, boolean activo) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre)
                .precioUsd(usd == null ? null : new BigDecimal(usd))
                .precio(new BigDecimal(ars))
                .stockActual(5)
                .stockMinimo(1)
                .activo(activo)
                .categoria(categoria)
                .build());
    }

    private PreciosAjusteRequest request(String tipo, String valor, String redondeo,
                                         String alcanceTipo, List<Long> categoriaIds,
                                         List<Long> productoIds) {
        return new PreciosAjusteRequest(tipo, new BigDecimal(valor), redondeo,
                new PreciosAjusteRequest.Alcance(alcanceTipo, categoriaIds, productoIds),
                null, null, null);
    }

    private PreciosAjusteRequest conToken(PreciosAjusteRequest original, String token,
                                          Boolean confirmar) {
        return new PreciosAjusteRequest(original.tipo(), original.valor(), original.redondeo(),
                original.alcance(), original.nota(), token, confirmar);
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
