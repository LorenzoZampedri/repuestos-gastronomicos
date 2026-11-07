package com.repuestos.services;

import com.repuestos.dto.LogsFiltros;
import com.repuestos.dto.PaginaLogsResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.LogActividad;
import com.repuestos.repositories.LogActividadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(LogsService.class)
class LogsServiceTest {

    @Autowired private LogsService logsService;
    @Autowired private LogActividadRepository logActividadRepository;

    private LogActividad adminVenta;
    private LogActividad empleadoProducto;
    private LogActividad adminLoginFallido;

    @BeforeEach
    void setUp() {
        logActividadRepository.deleteAll();

        adminVenta = guardar("admin", "ADMIN", AccionAuditoria.VENTA_CREADA,
                "Creó la venta #10", "OK", LocalDateTime.of(2026, 1, 10, 12, 0));
        empleadoProducto = guardar("empleado", "EMPLEADO", AccionAuditoria.PRODUCTO_EDITADO,
                "Editó el producto 'Heladera'", "OK", LocalDateTime.of(2026, 1, 12, 9, 30));
        adminLoginFallido = guardar("admin", "ADMIN", AccionAuditoria.LOGIN_FALLIDO,
                "Inicio de sesión fallido para 'admin'", "FALLIDO",
                LocalDateTime.of(2026, 1, 15, 20, 15));
    }

    private LogActividad guardar(String username, String rol, AccionAuditoria accion,
                                 String resumen, String resultado, LocalDateTime fecha) {
        return logActividadRepository.save(LogActividad.builder()
                .fecha(fecha)
                .usuarioId(1L)
                .usuarioUsername(username)
                .usuarioRol(rol)
                .accion(accion.name())
                .modulo(accion.getModulo())
                .entidadTipo("Entidad")
                .entidadId(1L)
                .resumen(resumen)
                .detalle("{}")
                .resultado(resultado)
                .ip("10.0.0.1")
                .userAgent("Test")
                .build());
    }

    private LogsFiltros filtros(Integer page, Integer size, LocalDate desde, LocalDate hasta,
                                String usuario, String rol, String modulo, String accion,
                                String resultado, String q) {
        return new LogsFiltros(page == null ? 0 : page,
                size == null ? 20 : size, desde, hasta, usuario, rol, modulo, accion,
                resultado, q);
    }

    @Test
    void ordenaPorFechaDescendenteYPagina() {
        PaginaLogsResponse pagina = logsService.buscar(
                filtros(0, 2, null, null, null, null, null, null, null, null));

        assertEquals(3, pagina.total());
        assertEquals(2, pagina.totalPaginas());
        assertEquals(2, pagina.content().size());
        assertEquals(adminLoginFallido.getId(), pagina.content().get(0).id());
        assertEquals(empleadoProducto.getId(), pagina.content().get(1).id());
    }

    @Test
    void limitaElTamanioMaximoDePagina() {
        PaginaLogsResponse pagina = logsService.buscar(
                filtros(0, 500, null, null, null, null, null, null, null, null));

        assertEquals(LogsService.TAMANIO_MAXIMO_PAGINA, pagina.size());
    }

    @Test
    void filtraPorUsuarioModuloAccionResultadoYTexto() {
        assertEquals(2, logsService.buscar(
                filtros(null, null, null, null, "admin", null, null, null, null, null)).total());

        assertEquals(1, logsService.buscar(
                filtros(null, null, null, null, null, "EMPLEADO", null, null, null, null)).total());

        assertEquals(1, logsService.buscar(
                filtros(null, null, null, null, null, null, "PRODUCTOS", null, null, null)).total());

        assertEquals(1, logsService.buscar(
                filtros(null, null, null, null, null, null, null, "LOGIN_FALLIDO", null, null)).total());

        assertEquals(1, logsService.buscar(
                filtros(null, null, null, null, null, null, null, null, "FALLIDO", null)).total());

        assertEquals(1, logsService.buscar(
                filtros(null, null, null, null, null, null, null, null, null, "heladera")).total());
    }

    @Test
    void filtraPorRangoDeFechasInclusive() {
        LocalDate desde = LocalDate.of(2026, 1, 12);
        LocalDate hasta = LocalDate.of(2026, 1, 15);

        PaginaLogsResponse pagina = logsService.buscar(
                filtros(null, null, desde, hasta, null, null, null, null, null, null));

        assertEquals(2, pagina.total());
        assertEquals(adminLoginFallido.getId(), pagina.content().get(0).id());
    }

    @Test
    void obtenerDevuelveDetalleYFallaSiNoExiste() {
        assertEquals(adminVenta.getId(), logsService.obtener(adminVenta.getId()).id());

        NegocioException error = assertThrows(NegocioException.class,
                () -> logsService.obtener(999999L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void exportaCsvConBomCabeceraEscapesYProteccionDeFormulas() {
        logActividadRepository.save(LogActividad.builder()
                .fecha(LocalDateTime.of(2026, 1, 20, 8, 0))
                .usuarioUsername("admin")
                .usuarioRol("ADMIN")
                .accion(AccionAuditoria.CONFIG_EDITADA.name())
                .modulo("CONFIGURACION")
                .resumen("=SUM(A1:A2)")
                .detalle("{\"nota\":\"dice \\\"hola\\\"\"}")
                .resultado("OK")
                .build());

        LogsService.ExportacionCsv exportacion = logsService.exportar(
                filtros(0, 5000, null, null, null, null, null, null, null, null));

        String csv = exportacion.contenido();
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("Fecha y hora,Usuario,Rol,Modulo,Accion"));
        assertTrue(csv.contains("\"'=SUM(A1:A2)\""));
        assertTrue(csv.contains("nota"));
        assertTrue(csv.contains("hola"));
        assertFalse(exportacion.truncado());
        assertEquals(4, exportacion.filas());
        assertEquals(4, exportacion.total());
    }

    @Test
    void filtrosDisponiblesExponeModulosAccionesYRoles() {
        Map<String, Object> disponibles = logsService.filtrosDisponibles();

        assertTrue(disponibles.containsKey("modulos"));
        assertTrue(disponibles.containsKey("acciones"));
        assertTrue(disponibles.containsKey("roles"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> acciones = (List<Map<String, String>>) disponibles.get("acciones");
        assertTrue(acciones.stream().anyMatch(a -> "VENTA_CREADA".equals(a.get("codigo"))));
    }
}
