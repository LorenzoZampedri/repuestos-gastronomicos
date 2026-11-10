package com.repuestos.dto;

import com.repuestos.models.Usuario;
import com.repuestos.services.UsuarioUnicidad;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioValidacionTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<UsuarioCrearRequest>> validarUsername(String username) {
        return validator.validate(new UsuarioCrearRequest(
                username, "clave-segura", "Nombre", "correo@example.com", Usuario.Rol.EMPLEADO));
    }

    private Set<ConstraintViolation<RegisterRequest>> validarRegistro(String username) {
        return validator.validate(new RegisterRequest(
                username, "clave-segura", "Nombre", "correo@example.com"));
    }

    private void assertMensaje(Set<? extends ConstraintViolation<?>> violaciones, String esperado) {
        assertTrue(violaciones.stream()
                        .anyMatch(violacion -> esperado.equals(violacion.getMessage())),
                () -> "Se esperaba el mensaje '" + esperado + "' en " + violaciones);
    }

    @Test
    void aceptaUsernamesValidosConPuntoGuionYGuionBajo() {
        String[] validos = {
                "usuario.valido-1_ok",
                "abc",
                "a".repeat(50),
        };

        for (String username : validos) {
            Set<ConstraintViolation<UsuarioCrearRequest>> violaciones = validarUsername(username);
            assertTrue(violaciones.stream()
                    .noneMatch(violacion -> "username".equals(violacion.getPropertyPath().toString())),
                    username);
        }
    }

    @Test
    void rechazaLargosFueraDeRangoConMensajeDeLongitud() {
        for (String username : new String[]{"ab", "a".repeat(51)}) {
            Set<ConstraintViolation<UsuarioCrearRequest>> violaciones = validarUsername(username);
            assertMensaje(violaciones, UsuarioUnicidad.MSG_USERNAME_LARGO);
            assertTrue(violaciones.stream()
                    .anyMatch(violacion -> "username".equals(violacion.getPropertyPath().toString())));
        }
    }

    @Test
    void rechazaCaracteresFueraDelPatronConMensajeDeFormato() {
        String[] invalidos = {
                "con espacio",
                "acentuado-á",
                "zero\u200b",
                "cirílica-а",
                "barra/x",
                "emoji-😀",
        };

        for (String username : invalidos) {
            Set<ConstraintViolation<UsuarioCrearRequest>> violaciones = validarUsername(username);
            assertMensaje(violaciones, UsuarioUnicidad.MSG_USERNAME_FORMATO);
            assertTrue(violaciones.stream()
                    .anyMatch(violacion -> "username".equals(violacion.getPropertyPath().toString())));
        }
    }

    @Test
    void elRegistroPublicoAplicaLasMismasReglasDeUsername() {
        assertEquals(UsuarioUnicidad.MSG_USERNAME_FORMATO,
                validarRegistro("con espacio").stream()
                        .filter(violacion -> "username".equals(
                                violacion.getPropertyPath().toString()))
                        .map(ConstraintViolation::getMessage)
                        .filter(UsuarioUnicidad.MSG_USERNAME_FORMATO::equals)
                        .findFirst()
                        .orElse(null));
        assertEquals(UsuarioUnicidad.MSG_USERNAME_LARGO,
                validarRegistro("ab").stream()
                        .filter(violacion -> "username".equals(
                                violacion.getPropertyPath().toString()))
                        .map(ConstraintViolation::getMessage)
                        .filter(UsuarioUnicidad.MSG_USERNAME_LARGO::equals)
                        .findFirst()
                        .orElse(null));
    }
}
