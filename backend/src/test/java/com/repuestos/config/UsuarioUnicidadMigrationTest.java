package com.repuestos.config;

import com.repuestos.models.Usuario;
import com.repuestos.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DataJpaTest
class UsuarioUnicidadMigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private UsuarioRepository usuarioRepository;

    private UsuarioUnicidadMigration migracion() {
        return new UsuarioUnicidadMigration(jdbcTemplate, dataSource);
    }

    private void sembrar(String username, String email) {
        usuarioRepository.saveAndFlush(Usuario.builder()
                .username(username)
                .passwordHash("hash")
                .nombre("Sembrado")
                .email(email)
                .rol(Usuario.Rol.EMPLEADO)
                .activo(true)
                .build());
    }

    @Test
    void detectaDuplicadosDeUsernameYEmailSembrados() {
        String sufijo = "-" + System.nanoTime();
        sembrar("Dup" + sufijo, "Dup" + sufijo + "@example.com");
        sembrar("dup" + sufijo, "dup" + sufijo + "@example.com");

        UsuarioUnicidadMigration migracion = migracion();
        List<UsuarioUnicidadMigration.Duplicado> usernames =
                migracion.detectarDuplicadosUsername();
        List<UsuarioUnicidadMigration.Duplicado> emails =
                migracion.detectarDuplicadosEmail();

        assertEquals(2, usernames.size());
        assertEquals(2, emails.size());
    }

    @Test
    void enH2LaMigracionSeOmiteYEsIdempotente() {
        UsuarioUnicidadMigration migracion = migracion();

        assertDoesNotThrow(() -> migracion.run());
        assertDoesNotThrow(() -> migracion.run());
    }

    @Test
    void conDuplicadosSembradosLaMigracionNoFrenaElArranque() {
        String sufijo = "-" + System.nanoTime();
        sembrar("Choque" + sufijo, null);
        sembrar("choque" + sufijo, null);
        UsuarioUnicidadMigration migracion = migracion();

        assertDoesNotThrow(() -> migracion.run());
        assertEquals(2, migracion.detectarDuplicadosUsername().size());
    }
}
