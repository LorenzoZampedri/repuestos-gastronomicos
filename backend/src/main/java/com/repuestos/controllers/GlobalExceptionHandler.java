package com.repuestos.controllers;

import com.repuestos.services.ImagenInvalidaException;
import com.repuestos.services.NegocioException;
import com.repuestos.services.UsuarioUnicidad;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> maxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "La imagen supera el máximo de 5 MB"));
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Map<String, Object>> negocio(NegocioException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        if (ex.getCode() != null) {
            body.put("code", ex.getCode());
        }
        if (ex.getDatos() != null) {
            body.putAll(ex.getDatos());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(ImagenInvalidaException.class)
    public ResponseEntity<Map<String, String>> imagenInvalida(ImagenInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> body = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .ifPresentOrElse(
                        error -> {
                            body.put("message", error.getDefaultMessage());
                            body.put("campo", error.getField());
                        },
                        () -> body.put("message", "Datos inválidos"));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> datosInvalidos(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Datos inválidos"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> dataIntegrityViolation(DataIntegrityViolationException ex) {
        NegocioException traducida = UsuarioUnicidad.desdeViolacion(ex);
        if (traducida != null) {
            log.warn("Violación de unicidad de usuario: {}", ex.getMostSpecificCause().getMessage());
            return negocio(traducida);
        }
        log.warn("Violación de integridad de datos: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "No se pudo guardar: algún dato ya existe o no es válido"));
    }
}
