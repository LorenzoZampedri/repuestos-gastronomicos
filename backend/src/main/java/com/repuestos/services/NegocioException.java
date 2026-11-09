package com.repuestos.services;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> datos;

    public NegocioException(HttpStatus status, String message) {
        this(status, message, null, null);
    }

    public NegocioException(HttpStatus status, String message, String code) {
        this(status, message, code, null);
    }

    public NegocioException(HttpStatus status, String message, String code,
                            Map<String, Object> datos) {
        super(message);
        this.status = status;
        this.code = code;
        this.datos = datos;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDatos() {
        return datos;
    }
}
