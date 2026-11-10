package com.repuestos.controllers;

import com.repuestos.services.UsuarioUnicidad;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ResponseEntity<Map<String, Object>> traducir(String detalle) {
        return handler.dataIntegrityViolation(new DataIntegrityViolationException(
                "could not execute statement", new SQLException(detalle)));
    }

    @Test
    void traduceViolacionDeUsernameDePostgres() {
        ResponseEntity<Map<String, Object>> respuesta = traducir(
                "duplicate key value violates unique constraint \"uk_usuarios_username_lower\"");

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals(UsuarioUnicidad.CODE_USERNAME_DUPLICADO,
                respuesta.getBody().get("code"));
        assertEquals("username", respuesta.getBody().get("campo"));
        assertEquals(UsuarioUnicidad.MSG_USERNAME_DUPLICADO,
                respuesta.getBody().get("message"));
    }

    @Test
    void traduceViolacionDeUsernameDeH2() {
        String detalle = "Unique index or primary key violation: \"PUBLIC.UK_USUARIOS_USERNAME_LOWER_INDEX_8 "
                + "ON PUBLIC.USUARIOS(USERNAME NULLS FIRST) VALUES ( /* 1 */ 'Prueba1' )\"; "
                + "SQL statement:\n insert into usuarios (username) values (?) [23505-214]";

        ResponseEntity<Map<String, Object>> respuesta = traducir(detalle);

        assertEquals(UsuarioUnicidad.CODE_USERNAME_DUPLICADO,
                respuesta.getBody().get("code"));
        assertEquals("username", respuesta.getBody().get("campo"));
    }

    @Test
    void traduceViolacionDeEmailDePostgresYDeH2() {
        ResponseEntity<Map<String, Object>> postgres = traducir(
                "duplicate key value violates unique constraint \"uk_usuarios_email_lower\"");
        ResponseEntity<Map<String, Object>> h2 = traducir(
                "Unique index or primary key violation: \"PUBLIC.UK_USUARIOS_EMAIL_LOWER_INDEX_2 "
                        + "ON PUBLIC.USUARIOS(EMAIL NULLS FIRST) VALUES ( /* 1 */ 'a@b.com' )\"; "
                        + "SQL statement:\n insert into usuarios (email) values (?) [23505-214]");

        assertEquals(UsuarioUnicidad.CODE_EMAIL_DUPLICADO,
                postgres.getBody().get("code"));
        assertEquals("email", postgres.getBody().get("campo"));
        assertEquals(UsuarioUnicidad.CODE_EMAIL_DUPLICADO,
                h2.getBody().get("code"));
        assertEquals("email", h2.getBody().get("campo"));
    }

    @Test
    void mantieneElMensajeGenericoParaOtrasViolaciones() {
        ResponseEntity<Map<String, Object>> respuesta = traducir(
                "duplicate key value violates unique constraint \"productos_codigo_barras_key\"");

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals("No se pudo guardar: algún dato ya existe o no es válido",
                respuesta.getBody().get("message"));
        assertFalse(respuesta.getBody().containsKey("code"));
        assertFalse(respuesta.getBody().containsKey("campo"));
        assertNull(respuesta.getBody().get("code"));
    }
}
