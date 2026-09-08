package com.repuestos.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
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
        
        // Clientes
        stats.put("clientesConDeuda", clienteService.clientesConDeuda().size());
        
        return stats;
    }
}
