package com.repuestos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.repuestos.models.LogActividad;

import java.time.LocalDateTime;

public record LogActividadResponse(
        Long id,
        LocalDateTime fecha,
        Long usuarioId,
        String usuarioUsername,
        String usuarioRol,
        String accion,
        String modulo,
        String entidadTipo,
        Long entidadId,
        String resumen,
        JsonNode detalle,
        String resultado,
        String ip,
        String userAgent) {

    public static LogActividadResponse desde(LogActividad log, JsonNode detalle) {
        return new LogActividadResponse(
                log.getId(),
                log.getFecha(),
                log.getUsuarioId(),
                log.getUsuarioUsername(),
                log.getUsuarioRol(),
                log.getAccion(),
                log.getModulo(),
                log.getEntidadTipo(),
                log.getEntidadId(),
                log.getResumen(),
                detalle,
                log.getResultado(),
                log.getIp(),
                log.getUserAgent());
    }
}
