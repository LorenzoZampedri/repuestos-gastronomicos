package com.repuestos.controllers;

import com.repuestos.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    
    private final DashboardService dashboardService;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> estadisticas() {
        return ResponseEntity.ok(dashboardService.obtenerEstadisticas());
    }

    @GetMapping("/ventas")
    public ResponseEntity<Map<String, Object>> ventas(@RequestParam(required = false) String dias) {
        Integer diasValidados = validarDias(dias);
        if (diasValidados == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "El parámetro 'dias' debe ser 7, 30 o 90"));
        }
        return ResponseEntity.ok(dashboardService.obtenerVentas(diasValidados));
    }

    private Integer validarDias(String valor) {
        if (valor == null || valor.isBlank()) {
            return 7;
        }
        try {
            int dias = Integer.parseInt(valor.trim());
            return (dias == 7 || dias == 30 || dias == 90) ? dias : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
