package com.repuestos.controllers;

import com.repuestos.models.Configuracion;
import com.repuestos.services.ConfiguracionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/configuracion")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Configuracion>> listarTodas() {
        return ResponseEntity.ok(configuracionService.listarTodas());
    }

    @PutMapping("/{clave}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Configuracion> guardarOActualizar(
            @PathVariable String clave,
            @RequestBody Map<String, String> body) {
        String valor = body.get("valor");
        if (valor == null || valor.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(configuracionService.guardarOActualizar(clave, valor.trim(), body.get("descripcion")));
    }

    @GetMapping("/publica")
    public ResponseEntity<Map<String, Object>> configuracionPublica() {
        Map<String, Object> response = new HashMap<>();
        response.put("cotizacionUsdArs", configuracionService.getCotizacionUsdArs());
        response.put("whatsappNumber", configuracionService.obtenerValor(ConfiguracionService.CLAVE_WHATSAPP_NUMBER));
        return ResponseEntity.ok(response);
    }
}
