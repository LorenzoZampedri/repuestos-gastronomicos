package com.repuestos.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reglas compartidas de unicidad y formato de usuarios. Se usan tanto en el
 * servicio como en la traducción de violaciones de integridad de la base.
 */
public final class UsuarioUnicidad {

    public static final String CODE_USERNAME_DUPLICADO = "USERNAME_DUPLICADO";
    public static final String CODE_EMAIL_DUPLICADO = "EMAIL_DUPLICADO";
    public static final String MSG_USERNAME_DUPLICADO =
            "Ese nombre de usuario ya está en uso. Elegí otro.";
    public static final String MSG_EMAIL_DUPLICADO = "Ya existe una cuenta con ese email.";
    public static final String MSG_USERNAME_FORMATO =
            "Usá solo letras sin acentos, números, punto, guion o guion bajo";
    public static final String MSG_USERNAME_LARGO =
            "El nombre de usuario debe tener entre 3 y 50 caracteres";

    public static final String REGEX_USERNAME = "^[A-Za-z0-9._-]{3,50}$";
    public static final Pattern PATRON_USERNAME = Pattern.compile(REGEX_USERNAME);

    private UsuarioUnicidad() {
    }

    public static NegocioException usernameDuplicado() {
        return new NegocioException(HttpStatus.CONFLICT, MSG_USERNAME_DUPLICADO,
                CODE_USERNAME_DUPLICADO, Map.of("campo", "username"));
    }

    public static NegocioException emailDuplicado() {
        return new NegocioException(HttpStatus.CONFLICT, MSG_EMAIL_DUPLICADO,
                CODE_EMAIL_DUPLICADO, Map.of("campo", "email"));
    }

    /**
     * Traduce una violación de integridad de username/email a la excepción de
     * negocio específica. Devuelve {@code null} si la violación no está
     * relacionada con esos campos, para conservar el mensaje genérico.
     */
    public static NegocioException desdeViolacion(DataIntegrityViolationException ex) {
        String mensaje = textoDe(ex);
        if (mensaje.isBlank()) {
            return null;
        }
        String normalizado = mensaje.toLowerCase(Locale.ROOT);
        int sql = normalizado.indexOf("; sql statement:");
        if (sql >= 0) {
            normalizado = normalizado.substring(0, sql);
        }

        boolean username = normalizado.contains("lower(username)")
                || normalizado.contains("usuarios_username")
                || normalizado.contains("usuarios(username")
                || normalizado.contains("usuarios (username");
        boolean email = normalizado.contains("lower(email)")
                || normalizado.contains("usuarios_email")
                || normalizado.contains("usuarios(email")
                || normalizado.contains("usuarios (email");

        if (username && !email) {
            return usernameDuplicado();
        }
        if (email && !username) {
            return emailDuplicado();
        }
        return null;
    }

    private static String textoDe(DataIntegrityViolationException ex) {
        StringBuilder texto = new StringBuilder();
        Throwable actual = ex;
        int profundidad = 0;
        while (actual != null && profundidad++ < 10) {
            if (actual.getMessage() != null) {
                texto.append(' ').append(actual.getMessage());
            }
            if (actual.getCause() == actual) {
                break;
            }
            actual = actual.getCause();
        }
        return texto.toString();
    }
}
