package com.repuestos.controllers;

import com.repuestos.dto.LogActividadResponse;
import com.repuestos.dto.LogsFiltros;
import com.repuestos.dto.PaginaLogsResponse;
import com.repuestos.services.LogsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogsController {

    private final LogsService logsService;

    @GetMapping
    public ResponseEntity<PaginaLogsResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) String q) {
        LogsFiltros filtros = new LogsFiltros(
                page, size, desde, hasta, usuario, rol, modulo, accion, resultado, q);
        return ResponseEntity.ok(logsService.buscar(filtros));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LogActividadResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(logsService.obtener(id));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) String q) {
        LogsFiltros filtros = new LogsFiltros(
                0, LogsService.TAMANIO_EXPORTACION, desde, hasta,
                usuario, rol, modulo, accion, resultado, q);
        LogsService.ExportacionCsv exportacion = logsService.exportar(filtros);

        String nombre = "logs-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                + ".csv";
        byte[] contenido = exportacion.contenido().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + URLEncoder.encode(nombre, StandardCharsets.UTF_8) + "\"")
                .header("X-Logs-Truncados", String.valueOf(exportacion.truncado()))
                .header("X-Logs-Filas", String.valueOf(exportacion.filas()))
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "X-Logs-Truncados, X-Logs-Filas, Content-Disposition")
                .body(contenido);
    }

    @GetMapping("/filtros")
    public ResponseEntity<Map<String, Object>> filtros() {
        return ResponseEntity.ok(logsService.filtrosDisponibles());
    }
}
