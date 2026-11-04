package com.repuestos.services;

import com.repuestos.repositories.DetalleVentaRepository;
import com.repuestos.repositories.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String[] NOMBRES_DIA = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final DateTimeFormatter FORMATO_DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final ProductoService productoService;
    private final VentaService ventaService;
    private final ClienteService clienteService;
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;

    public Map<String, Object> obtenerEstadisticas() {
        Map<String, Object> stats = new HashMap<>();

        // Productos
        stats.put("totalProductos", productoService.contarProductosActivos());
        stats.put("productosStockBajo", productoService.productosStockBajo().size());

        // Ventas
        stats.put("ventasDelDia", ventaService.ventasDelDia());
        stats.put("totalVentas", ventaService.totalVentas());

        // Clientes
        stats.put("clientesConDeuda", clienteService.clientesConDeuda().size());

        return stats;
    }

    public Map<String, Object> obtenerVentas(int dias) {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = hoy.minusDays(dias - 1);
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime finExclusivo = hoy.plusDays(1).atStartOfDay();

        Map<LocalDate, BigDecimal> montosPorDia = new HashMap<>();
        Map<LocalDate, Long> ventasPorDia = new HashMap<>();
        for (Object[] fila : ventaRepository.agregadoVentasPorDia(inicio, finExclusivo)) {
            LocalDate dia = aFecha(fila[0]);
            montosPorDia.put(dia, aBigDecimal(fila[1]));
            ventasPorDia.put(dia, aLong(fila[2]));
        }

        Map<LocalDate, Long> productosPorDia = new HashMap<>();
        for (Object[] fila : detalleVentaRepository.agregadoProductosPorDia(inicio, finExclusivo)) {
            productosPorDia.put(aFecha(fila[0]), aLong(fila[1]));
        }

        return construirRespuestaVentas(dias, hoy, montosPorDia, ventasPorDia, productosPorDia);
    }

    static Map<String, Object> construirRespuestaVentas(
            int dias,
            LocalDate hoy,
            Map<LocalDate, BigDecimal> montosPorDia,
            Map<LocalDate, Long> ventasPorDia,
            Map<LocalDate, Long> productosPorDia) {

        LocalDate desde = hoy.minusDays(dias - 1);
        LocalDate hasta = hoy;

        String agrupacion;
        List<Map<String, Object>> serie;
        if (dias == 90) {
            agrupacion = "semana";
            serie = construirSerieSemanal(desde, hasta, montosPorDia, ventasPorDia, productosPorDia);
        } else {
            agrupacion = "dia";
            serie = construirSerieDiaria(dias, desde, hasta, montosPorDia, ventasPorDia, productosPorDia);
        }

        BigDecimal totalMonto = BigDecimal.ZERO;
        long totalVentas = 0;
        long totalProductos = 0;
        for (Map<String, Object> item : serie) {
            totalMonto = totalMonto.add((BigDecimal) item.get("monto"));
            totalVentas += (Long) item.get("cantidadVentas");
            totalProductos += (Long) item.get("cantidadProductos");
        }

        Map<String, Object> totales = new LinkedHashMap<>();
        totales.put("monto", totalMonto);
        totales.put("cantidadVentas", totalVentas);
        totales.put("cantidadProductos", totalProductos);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("dias", dias);
        respuesta.put("agrupacion", agrupacion);
        respuesta.put("desde", desde.toString());
        respuesta.put("hasta", hasta.toString());
        respuesta.put("totales", totales);
        respuesta.put("serie", serie);
        return respuesta;
    }

    private static List<Map<String, Object>> construirSerieDiaria(
            int dias,
            LocalDate desde,
            LocalDate hasta,
            Map<LocalDate, BigDecimal> montosPorDia,
            Map<LocalDate, Long> ventasPorDia,
            Map<LocalDate, Long> productosPorDia) {

        List<Map<String, Object>> serie = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            String nombreDia = NOMBRES_DIA[dia.getDayOfWeek().getValue() - 1];
            String fecha = dia.toString();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", dias == 7 ? nombreDia : dia.format(FORMATO_DIA_MES));
            item.put("etiqueta", nombreDia + " " + dia.format(FORMATO_DIA_MES));
            item.put("fecha", fecha);
            item.put("fechaFin", fecha);
            item.put("monto", valorMonto(montosPorDia, dia));
            item.put("cantidadVentas", valorConteo(ventasPorDia, dia));
            item.put("cantidadProductos", valorConteo(productosPorDia, dia));
            serie.add(item);
        }
        return serie;
    }

    private static List<Map<String, Object>> construirSerieSemanal(
            LocalDate desde,
            LocalDate hasta,
            Map<LocalDate, BigDecimal> montosPorDia,
            Map<LocalDate, Long> ventasPorDia,
            Map<LocalDate, Long> productosPorDia) {

        List<Map<String, Object>> bloques = new ArrayList<>();
        LocalDate finBloque = hasta;
        while (!finBloque.isBefore(desde)) {
            LocalDate inicioBloque = finBloque.minusDays(6);
            if (inicioBloque.isBefore(desde)) {
                inicioBloque = desde;
            }

            BigDecimal monto = BigDecimal.ZERO;
            long cantidadVentas = 0;
            long cantidadProductos = 0;
            for (LocalDate dia = inicioBloque; !dia.isAfter(finBloque); dia = dia.plusDays(1)) {
                monto = monto.add(valorMonto(montosPorDia, dia));
                cantidadVentas += valorConteo(ventasPorDia, dia);
                cantidadProductos += valorConteo(productosPorDia, dia);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", inicioBloque.format(FORMATO_DIA_MES));
            item.put("etiqueta", inicioBloque.format(FORMATO_DIA_MES)
                    + " – " + finBloque.format(FORMATO_DIA_MES));
            item.put("fecha", inicioBloque.toString());
            item.put("fechaFin", finBloque.toString());
            item.put("monto", monto);
            item.put("cantidadVentas", cantidadVentas);
            item.put("cantidadProductos", cantidadProductos);
            bloques.add(item);

            finBloque = inicioBloque.minusDays(1);
        }

        List<Map<String, Object>> serie = new ArrayList<>(bloques.size());
        for (int i = bloques.size() - 1; i >= 0; i--) {
            serie.add(bloques.get(i));
        }
        return serie;
    }

    private static BigDecimal valorMonto(Map<LocalDate, BigDecimal> valores, LocalDate dia) {
        BigDecimal valor = valores.get(dia);
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static Long valorConteo(Map<LocalDate, Long> valores, LocalDate dia) {
        Long valor = valores.get(dia);
        return valor == null ? 0L : valor;
    }

    private static LocalDate aFecha(Object valor) {
        if (valor instanceof LocalDate localDate) {
            return localDate;
        }
        if (valor instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (valor instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(valor.toString());
    }

    private static BigDecimal aBigDecimal(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        if (valor instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(valor.toString());
    }

    private static Long aLong(Object valor) {
        if (valor == null) {
            return 0L;
        }
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        return Long.parseLong(valor.toString());
    }
}
