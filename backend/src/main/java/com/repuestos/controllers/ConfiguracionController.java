package com.repuestos.controllers;

import com.repuestos.dto.DatosTransferenciaRequest;
import com.repuestos.dto.DatosTransferenciaResponse;
import com.repuestos.models.Configuracion;
import com.repuestos.models.ConfiguracionHistorial;
import com.repuestos.services.ConfiguracionService;
import com.repuestos.services.NegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PutMapping("/transferencia")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DatosTransferenciaResponse> guardarTransferencia(
            @RequestBody DatosTransferenciaRequest request) {
        return ResponseEntity.ok(configuracionService.guardarDatosTransferencia(request));
    }

    @PutMapping("/{clave}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Configuracion> guardarOActualizar(
            @PathVariable String clave,
            @RequestBody Map<String, String> body) {
        if (configuracionService.esClaveTransferencia(clave)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Los datos de transferencia se editan desde el formulario de datos de transferencia");
        }
        String valor = body.get("valor");
        if (valor == null || valor.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(configuracionService.guardarOActualizar(clave, valor.trim(), body.get("descripcion")));
    }

    @GetMapping("/historial")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConfiguracionHistorial>> historial(@RequestParam String clave) {
        return ResponseEntity.ok(configuracionService.listarHistorial(clave));
    }

    @GetMapping("/publica")
    public ResponseEntity<Map<String, Object>> configuracionPublica() {
        Map<String, Object> response = new HashMap<>();
        response.put("cotizacionUsdArs", configuracionService.getCotizacionUsdArs());
        response.put("whatsappNumber", configuracionService.obtenerValor(ConfiguracionService.CLAVE_WHATSAPP_NUMBER));
        response.put("datosTransferencia", configuracionService.obtenerDatosTransferencia());
        return ResponseEntity.ok(response);
    }
}
