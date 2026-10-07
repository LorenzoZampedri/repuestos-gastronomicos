package com.repuestos.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final ProductoService productoService;
    private final VentaService ventaService;
    private final ClienteService clienteService;
    
    public Map<String, Object> obtenerEstadisticas() {
        Map<String, Object> stats = new HashMap<>();
        
        // Productos
        stats.put("totalProductos", productoService.contarProductosActivos());
        stats.put("productosStockBajo", productoService.productosStockBajo().size());
        
        // Ventas
        stats.put("ventasDelDia", ventaService.ventasDelDia());
        stats.put("totalVentas", ventaService.totalVentas());
        stats.put("ventasSemana", ventasUltimosDias(7));
        
        // Clientes
        stats.put("clientesConDeuda", clienteService.clientesConDeuda().size());
        
        return stats;
    }
    
    private List<Map<String, Object>> ventasUltimosDias(int dias) {
        String[] nombres = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        List<Map<String, Object>> resultado = new ArrayList<>();
        LocalDate hoy = LocalDate.now();
        
        for (int i = dias - 1; i >= 0; i--) {
            LocalDate dia = hoy.minusDays(i);
            LocalDateTime inicio = dia.atStartOfDay();
            LocalDateTime fin = dia.atTime(LocalTime.MAX);
            BigDecimal total = ventaService.ventasPorPeriodo(inicio, fin);
            
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", nombres[dia.getDayOfWeek().getValue() - 1]);
            item.put("fecha", dia.toString());
            item.put("ventas", total);
            resultado.add(item);
        }
        
        return resultado;
    }
}
