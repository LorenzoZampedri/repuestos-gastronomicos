package com.repuestos.controllers;

import com.repuestos.dto.LotePreciosDetalleResponse;
import com.repuestos.dto.PaginaLotesResponse;
import com.repuestos.dto.PreciosAjusteRequest;
import com.repuestos.dto.PreciosAplicarResponse;
import com.repuestos.dto.PreciosEstadoResponse;
import com.repuestos.dto.PreciosRecalcularResponse;
import com.repuestos.dto.PreciosRevertirResponse;
import com.repuestos.dto.PreciosVistaPreviaResponse;
import com.repuestos.services.PreciosMasivosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/precios")
@RequiredArgsConstructor
public class PreciosController {

    private final PreciosMasivosService preciosMasivosService;

    @PostMapping("/vista-previa")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PreciosVistaPreviaResponse> vistaPrevista(
            @Valid @RequestBody PreciosAjusteRequest request) {
        return ResponseEntity.ok(preciosMasivosService.vistaPrevia(request));
    }

    @PostMapping("/aplicar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PreciosAplicarResponse> aplicar(
            @Valid @RequestBody PreciosAjusteRequest request) {
        return ResponseEntity.ok(preciosMasivosService.aplicar(request));
    }

    @GetMapping("/lotes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaginaLotesResponse> listarLotes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(preciosMasivosService.listarLotes(page, size));
    }

    @GetMapping("/lotes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LotePreciosDetalleResponse> detalleLote(@PathVariable Long id) {
        return ResponseEntity.ok(preciosMasivosService.detalleLote(id));
    }

    @PostMapping("/lotes/{id}/revertir")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PreciosRevertirResponse> revertir(@PathVariable Long id) {
        return ResponseEntity.ok(preciosMasivosService.revertir(id));
    }

    @GetMapping("/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PreciosEstadoResponse> estado() {
        return ResponseEntity.ok(preciosMasivosService.estado());
    }

    @PostMapping("/recalcular-ars")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PreciosRecalcularResponse> recalcularArs() {
        return ResponseEntity.ok(preciosMasivosService.recalcularArs());
    }
}
