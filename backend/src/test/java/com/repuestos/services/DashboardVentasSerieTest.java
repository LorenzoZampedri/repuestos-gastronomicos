package com.repuestos.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardVentasSerieTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 4);

    @Test
    void dias7DevuelveSerieDiariaCompletaConCeros() {
        Map<LocalDate, BigDecimal> montos = new HashMap<>();
        Map<LocalDate, Long> ventas = new HashMap<>();
        Map<LocalDate, Long> productos = new HashMap<>();
        montos.put(HOY, new BigDecimal("1500.50"));
        ventas.put(HOY, 2L);
        productos.put(HOY, 5L);

        Map<String, Object> respuesta = DashboardService.construirRespuestaVentas(
                7, HOY, montos, ventas, productos);
        List<Map<String, Object>> serie = serie(respuesta);

        assertEquals(7, serie.size());
        assertEquals("dia", respuesta.get("agrupacion"));
        assertEquals(HOY.minusDays(6).toString(), respuesta.get("desde"));
        assertEquals(HOY.toString(), respuesta.get("hasta"));
        assertEquals(HOY.minusDays(6).toString(), serie.get(0).get("fecha"));
        assertEquals(HOY.toString(), serie.get(6).get("fecha"));
        assertEquals("Jue", serie.get(0).get("name"));
        assertEquals("Jue 29/10", serie.get(0).get("etiqueta"));
        assertEquals("Mié", serie.get(6).get("name"));
        assertEquals("Mié 04/11", serie.get(6).get("etiqueta"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) serie.get(0).get("monto")));
        assertEquals(0L, serie.get(0).get("cantidadVentas"));
        assertEquals(0L, serie.get(0).get("cantidadProductos"));
        assertEquals(new BigDecimal("1500.50"), serie.get(6).get("monto"));
        assertEquals(2L, serie.get(6).get("cantidadVentas"));
        assertEquals(5L, serie.get(6).get("cantidadProductos"));
        assertTotales(respuesta, new BigDecimal("1500.50"), 2L, 5L);
    }

    @Test
    void dias30DevuelveTreintaDiasEnOrdenConEtiquetasDdMm() {
        Map<String, Object> respuesta = DashboardService.construirRespuestaVentas(
                30, HOY, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        List<Map<String, Object>> serie = serie(respuesta);

        assertEquals(30, serie.size());
        assertEquals("dia", respuesta.get("agrupacion"));
        assertEquals(HOY.minusDays(29).toString(), serie.get(0).get("fecha"));
        assertEquals(HOY.toString(), serie.get(29).get("fecha"));
        assertEquals("06/10", serie.get(0).get("name"));
        assertEquals("Mar 06/10", serie.get(0).get("etiqueta"));
        assertEquals("04/11", serie.get(29).get("name"));
        assertEquals("Mié 04/11", serie.get(29).get("etiqueta"));
        assertFechasAscendentes(serie);
        assertTotales(respuesta, BigDecimal.ZERO, 0L, 0L);
    }

    @Test
    void dias90DevuelveDoceBloquesDeSieteDiasYUnBloqueDeSeis() {
        Map<LocalDate, BigDecimal> montos = new HashMap<>();
        Map<LocalDate, Long> ventas = new HashMap<>();
        Map<LocalDate, Long> productos = new HashMap<>();
        montos.put(HOY, new BigDecimal("100"));
        montos.put(HOY.minusDays(89), new BigDecimal("50"));
        ventas.put(HOY, 1L);
        ventas.put(HOY.minusDays(89), 1L);
        productos.put(HOY, 3L);
        productos.put(HOY.minusDays(89), 2L);

        Map<String, Object> respuesta = DashboardService.construirRespuestaVentas(
                90, HOY, montos, ventas, productos);
        List<Map<String, Object>> serie = serie(respuesta);

        assertEquals(13, serie.size());
        assertEquals("semana", respuesta.get("agrupacion"));
        assertEquals(HOY.minusDays(89).toString(), respuesta.get("desde"));
        assertEquals(HOY.toString(), respuesta.get("hasta"));
        assertFechasAscendentes(serie);

        Map<String, Object> primerBloque = serie.get(0);
        LocalDate inicioPrimero = LocalDate.parse((String) primerBloque.get("fecha"));
        LocalDate finPrimero = LocalDate.parse((String) primerBloque.get("fechaFin"));
        assertEquals(5, ChronoUnit.DAYS.between(inicioPrimero, finPrimero));

        for (int i = 1; i < serie.size(); i++) {
            Map<String, Object> bloque = serie.get(i);
            LocalDate inicio = LocalDate.parse((String) bloque.get("fecha"));
            LocalDate fin = LocalDate.parse((String) bloque.get("fechaFin"));
            assertEquals(6, ChronoUnit.DAYS.between(inicio, fin));
        }

        Map<String, Object> ultimoBloque = serie.get(12);
        assertEquals(HOY.minusDays(6).toString(), ultimoBloque.get("fecha"));
        assertEquals(HOY.toString(), ultimoBloque.get("fechaFin"));
        assertEquals(new BigDecimal("100"), ultimoBloque.get("monto"));
        assertEquals(1L, ultimoBloque.get("cantidadVentas"));
        assertEquals(3L, ultimoBloque.get("cantidadProductos"));
        assertEquals(new BigDecimal("50"), primerBloque.get("monto"));
        assertEquals(1L, primerBloque.get("cantidadVentas"));
        assertEquals(2L, primerBloque.get("cantidadProductos"));
        assertTotales(respuesta, new BigDecimal("150"), 2L, 5L);
    }

    @Test
    void totalesCoincidenConLaSumaDeLaSerie() {
        Map<LocalDate, BigDecimal> montos = new HashMap<>();
        Map<LocalDate, Long> ventas = new HashMap<>();
        Map<LocalDate, Long> productos = new HashMap<>();
        for (int i = 0; i < 90; i++) {
            LocalDate dia = HOY.minusDays(i);
            montos.put(dia, BigDecimal.valueOf(i + 1));
            ventas.put(dia, 1L);
            productos.put(dia, 2L);
        }

        Map<String, Object> respuesta = DashboardService.construirRespuestaVentas(
                90, HOY, montos, ventas, productos);

        BigDecimal montoSerie = BigDecimal.ZERO;
        long ventasSerie = 0;
        long productosSerie = 0;
        for (Map<String, Object> item : serie(respuesta)) {
            montoSerie = montoSerie.add((BigDecimal) item.get("monto"));
            ventasSerie += (Long) item.get("cantidadVentas");
            productosSerie += (Long) item.get("cantidadProductos");
        }

        assertTotales(respuesta, montoSerie, ventasSerie, productosSerie);
        assertTotales(respuesta, new BigDecimal("4095"), 90L, 180L);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> serie(Map<String, Object> respuesta) {
        return (List<Map<String, Object>>) respuesta.get("serie");
    }

    private static void assertFechasAscendentes(List<Map<String, Object>> serie) {
        for (int i = 1; i < serie.size(); i++) {
            LocalDate anterior = LocalDate.parse((String) serie.get(i - 1).get("fecha"));
            LocalDate actual = LocalDate.parse((String) serie.get(i).get("fecha"));
            assertTrue(actual.isAfter(anterior), "La serie debe estar en orden cronológico");
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertTotales(Map<String, Object> respuesta, BigDecimal monto,
                                      long cantidadVentas, long cantidadProductos) {
        Map<String, Object> totales = (Map<String, Object>) respuesta.get("totales");
        assertEquals(monto, totales.get("monto"));
        assertEquals(cantidadVentas, totales.get("cantidadVentas"));
        assertEquals(cantidadProductos, totales.get("cantidadProductos"));
    }
}
