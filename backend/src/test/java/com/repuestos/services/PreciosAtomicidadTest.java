package com.repuestos.services;

import com.repuestos.dto.PreciosAjusteRequest;
import com.repuestos.dto.PreciosVistaPreviaResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.LotePreciosRepository;
import com.repuestos.repositories.ProductoRepository;
import com.repuestos.repositories.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * Todo o nada: si falla un paso a mitad de la actualización masiva (o del
 * recálculo al cambiar la cotización), no queda ni un precio modificado ni un
 * lote a medias. Corre sin transacción compartida (NOT_SUPPORTED) para leer el
 * estado realmente persistido después del rollback.
 */
@DataJpaTest
@Import({PreciosMasivosService.class, ConfiguracionService.class, AuditoriaService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PreciosAtomicidadTest {

    @Autowired private PreciosMasivosService preciosMasivosService;
    @Autowired private ConfiguracionService configuracionService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private LotePreciosRepository lotePreciosRepository;
    @Autowired private LogActividadRepository logActividadRepository;

    @SpyBean private AuditoriaService auditoriaService;

    private Producto producto;

    @BeforeEach
    void setUp() {
        logActividadRepository.deleteAll();
        lotePreciosRepository.deleteAll();
        productoRepository.deleteAll();

        Usuario admin = usuarioRepository.save(Usuario.builder()
                .username("admin-atomico-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Admin Atómico")
                .rol(Usuario.Rol.ADMIN)
                .activo(true)
                .build());
        autenticar(admin);

        configuracionService.guardarOActualizar(
                ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1000", "Cotización");

        producto = productoRepository.save(Producto.builder()
                .nombre("Atómico " + System.nanoTime())
                .precioUsd(new BigDecimal("10.00"))
                .precio(new BigDecimal("10000.00"))
                .stockActual(5)
                .stockMinimo(1)
                .activo(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void siFallaAlAuditarElAplicadoNoSeGuardaNiElLoteNiLosPrecios() {
        PreciosAjusteRequest request = request("PORCENTAJE", "10");
        PreciosVistaPreviaResponse preview = preciosMasivosService.vistaPrevia(request);

        doThrow(new IllegalStateException("fallo simulado"))
                .when(auditoriaService).registrar(
                        eq(AccionAuditoria.PRECIOS_ACTUALIZADOS_MASIVO),
                        anyString(), any(), anyString(), any());

        assertThrows(IllegalStateException.class, () ->
                preciosMasivosService.aplicar(conToken(request, preview.token())));

        Producto leido = productoRepository.findById(producto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("10.00").compareTo(leido.getPrecioUsd()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(leido.getPrecio()));
        assertEquals(0, lotePreciosRepository.count());
        assertEquals(0, contar(AccionAuditoria.PRECIOS_ACTUALIZADOS_MASIVO));
    }

    @Test
    void siFallaElRecalculoAlCambiarLaCotizacionNoQuedaGuardada() {
        doThrow(new IllegalStateException("fallo simulado"))
                .when(auditoriaService).registrar(
                        eq(AccionAuditoria.PRECIOS_RECALCULADOS),
                        anyString(), any(), anyString(), any());

        assertThrows(IllegalStateException.class, () ->
                configuracionService.guardarOActualizar(
                        ConfiguracionService.CLAVE_COTIZACION_USD_ARS, "1200", "Cotización"));

        assertEquals(0, new BigDecimal("1000")
                .compareTo(configuracionService.getCotizacionUsdArs()));
        Producto leido = productoRepository.findById(producto.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(leido.getPrecio()));
        assertEquals(0, contar(AccionAuditoria.PRECIOS_RECALCULADOS));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private PreciosAjusteRequest request(String tipo, String valor) {
        return new PreciosAjusteRequest(tipo, new BigDecimal(valor), "0.01",
                new PreciosAjusteRequest.Alcance("TODOS", null, null), null, null, null);
    }

    private PreciosAjusteRequest conToken(PreciosAjusteRequest original, String token) {
        return new PreciosAjusteRequest(original.tipo(), original.valor(), original.redondeo(),
                original.alcance(), original.nota(), token, null);
    }

    private void autenticar(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))));
    }

    private long contar(AccionAuditoria accion) {
        return logActividadRepository.findAll().stream()
                .filter(log -> accion.name().equals(log.getAccion()))
                .count();
    }
}
