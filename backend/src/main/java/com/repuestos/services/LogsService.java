package com.repuestos.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repuestos.dto.LogActividadResponse;
import com.repuestos.dto.LogsFiltros;
import com.repuestos.dto.PaginaLogsResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.LogActividad;
import com.repuestos.repositories.LogActividadRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LogsService {

    public static final int TAMANIO_MAXIMO_PAGINA = 100;
    public static final int TAMANIO_EXPORTACION = 5000;

    private static final DateTimeFormatter FORMATO_CSV =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final Sort ORDEN = Sort.by(
            Sort.Order.desc("fecha"), Sort.Order.desc("id"));

    private final LogActividadRepository logActividadRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public PaginaLogsResponse buscar(LogsFiltros filtros) {
        int pagina = Math.max(0, filtros.page());
        int tamanio = filtros.size() <= 0
                ? 20 : Math.min(filtros.size(), TAMANIO_MAXIMO_PAGINA);
        Pageable pageable = PageRequest.of(pagina, tamanio, ORDEN);
        Page<LogActividad> resultado = logActividadRepository.findAll(
                especificacion(filtros), pageable);

        List<LogActividadResponse> contenido = resultado.getContent().stream()
                .map(this::respuesta)
                .toList();
        return new PaginaLogsResponse(
                contenido,
                resultado.getNumber(),
                resultado.getTotalPages(),
                resultado.getTotalElements(),
                resultado.getSize());
    }

    @Transactional(readOnly = true)
    public LogActividadResponse obtener(Long id) {
        LogActividad log = logActividadRepository.findById(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Registro de auditoría no encontrado"));
        return respuesta(log);
    }

    @Transactional(readOnly = true)
    public ExportacionCsv exportar(LogsFiltros filtros) {
        Specification<LogActividad> spec = especificacion(filtros);
        long total = logActividadRepository.count(spec);
        Page<LogActividad> pagina = logActividadRepository.findAll(
                spec, PageRequest.of(0, TAMANIO_EXPORTACION, ORDEN));
        String csv = generarCsv(pagina.getContent());
        return new ExportacionCsv(csv, total > TAMANIO_EXPORTACION,
                pagina.getNumberOfElements(), total);
    }

    public Map<String, Object> filtrosDisponibles() {
        List<Map<String, String>> modulos = new ArrayList<>();
        Map<String, String> etiquetasModulo = new LinkedHashMap<>();
        for (AccionAuditoria accion : AccionAuditoria.values()) {
            etiquetasModulo.putIfAbsent(accion.getModulo(), etiquetaModulo(accion.getModulo()));
        }
        etiquetasModulo.forEach((codigo, etiqueta) -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("codigo", codigo);
            item.put("etiqueta", etiqueta);
            modulos.add(item);
        });

        List<Map<String, String>> acciones = new ArrayList<>();
        for (AccionAuditoria accion : AccionAuditoria.values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("codigo", accion.name());
            item.put("etiqueta", accion.getEtiqueta());
            item.put("modulo", accion.getModulo());
            acciones.add(item);
        }

        List<Map<String, String>> roles = new ArrayList<>();
        for (String rol : List.of("ADMIN", "EMPLEADO", "CLIENTE")) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("codigo", rol);
            item.put("etiqueta", etiquetaRol(rol));
            roles.add(item);
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("modulos", modulos);
        respuesta.put("acciones", acciones);
        respuesta.put("roles", roles);
        return respuesta;
    }

    private Specification<LogActividad> especificacion(LogsFiltros filtros) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (filtros.desde() != null) {
                predicados.add(cb.greaterThanOrEqualTo(
                        root.get("fecha"), filtros.desde().atStartOfDay()));
            }
            if (filtros.hasta() != null) {
                predicados.add(cb.lessThanOrEqualTo(
                        root.get("fecha"), filtros.hasta().atTime(LocalTime.MAX)));
            }
            if (texto(filtros.usuario())) {
                predicados.add(cb.like(cb.lower(root.get("usuarioUsername")),
                        "%" + filtros.usuario().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (texto(filtros.rol())) {
                predicados.add(cb.equal(cb.upper(root.get("usuarioRol")),
                        filtros.rol().trim().toUpperCase(Locale.ROOT)));
            }
            if (texto(filtros.modulo())) {
                predicados.add(cb.equal(cb.upper(root.get("modulo")),
                        filtros.modulo().trim().toUpperCase(Locale.ROOT)));
            }
            if (texto(filtros.accion())) {
                predicados.add(cb.equal(cb.upper(root.get("accion")),
                        filtros.accion().trim().toUpperCase(Locale.ROOT)));
            }
            if (texto(filtros.resultado())) {
                predicados.add(cb.equal(cb.upper(root.get("resultado")),
                        filtros.resultado().trim().toUpperCase(Locale.ROOT)));
            }
            if (texto(filtros.q())) {
                predicados.add(cb.like(cb.lower(root.get("resumen")),
                        "%" + filtros.q().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return predicados.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private LogActividadResponse respuesta(LogActividad log) {
        JsonNode detalle = null;
        if (log.getDetalle() != null && !log.getDetalle().isBlank()) {
            try {
                detalle = objectMapper.readTree(log.getDetalle());
            } catch (Exception e) {
                detalle = objectMapper.getNodeFactory().textNode(log.getDetalle());
            }
        }
        return LogActividadResponse.desde(log, detalle);
    }

    private String generarCsv(List<LogActividad> registros) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("Fecha y hora,Usuario,Rol,Modulo,Accion,Entidad,ID entidad,Resumen,Resultado,IP,Navegador,Detalle\r\n");
        for (LogActividad log : registros) {
            List<String> columnas = List.of(
                    log.getFecha() == null ? "" : log.getFecha().format(FORMATO_CSV),
                    nulo(log.getUsuarioUsername()),
                    nulo(log.getUsuarioRol()),
                    nulo(log.getModulo()),
                    etiquetaAccion(log.getAccion()),
                    nulo(log.getEntidadTipo()),
                    log.getEntidadId() == null ? "" : String.valueOf(log.getEntidadId()),
                    nulo(log.getResumen()),
                    nulo(log.getResultado()),
                    nulo(log.getIp()),
                    nulo(log.getUserAgent()),
                    nulo(log.getDetalle()));
            List<String> escapadas = new ArrayList<>(columnas.size());
            for (String columna : columnas) {
                escapadas.add(escaparCsv(columna));
            }
            sb.append(String.join(",", escapadas)).append("\r\n");
        }
        return sb.toString();
    }

    private String escaparCsv(String valor) {
        String seguro = valor == null ? "" : valor;
        if (!seguro.isEmpty()) {
            char primero = seguro.charAt(0);
            if (primero == '=' || primero == '+' || primero == '-' || primero == '@') {
                seguro = "'" + seguro;
            }
        }
        seguro = seguro.replace("\r", " ").replace("\n", " ");
        return "\"" + seguro.replace("\"", "\"\"") + "\"";
    }

    private String etiquetaAccion(String codigo) {
        try {
            AccionAuditoria accion = AccionAuditoria.valueOf(codigo);
            return accion.getEtiqueta() + " (" + codigo + ")";
        } catch (IllegalArgumentException | NullPointerException e) {
            return nulo(codigo);
        }
    }

    private String etiquetaModulo(String modulo) {
        return switch (modulo) {
            case "AUTH" -> "Autenticación";
            case "USUARIOS" -> "Usuarios";
            case "PRODUCTOS" -> "Productos";
            case "CLIENTES" -> "Clientes";
            case "VENTAS" -> "Ventas y pagos";
            case "PEDIDOS" -> "Pedidos";
            case "CONFIGURACION" -> "Configuración";
            case "SEGURIDAD" -> "Seguridad";
            default -> modulo;
        };
    }

    private String etiquetaRol(String rol) {
        return switch (rol) {
            case "ADMIN" -> "Administrador";
            case "EMPLEADO" -> "Empleado";
            case "CLIENTE" -> "Cliente";
            default -> rol;
        };
    }

    private boolean texto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String nulo(String valor) {
        return valor == null ? "" : valor;
    }

    public record ExportacionCsv(String contenido, boolean truncado, int filas, long total) {
    }
}
