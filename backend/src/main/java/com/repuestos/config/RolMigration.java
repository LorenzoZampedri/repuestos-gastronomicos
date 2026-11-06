package com.repuestos.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RolMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String motor = connection.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equalsIgnoreCase(motor)) {
                log.info("Migración de roles omitida: el motor {} no requiere ajustar el constraint", motor);
                return;
            }
        }

        jdbcTemplate.execute("ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check");
        int migrados = jdbcTemplate.update(
                "UPDATE usuarios SET rol = 'EMPLEADO' WHERE rol IN ('VENDEDOR', 'OPERARIO')");
        jdbcTemplate.execute("ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check "
                + "CHECK (rol IN ('ADMIN', 'EMPLEADO', 'CLIENTE'))");

        if (migrados > 0) {
            log.info("Migración de roles: {} usuario(s) VENDEDOR/OPERARIO pasaron a EMPLEADO", migrados);
        } else {
            log.info("Migración de roles verificada: constraint usuarios_rol_check actualizado");
        }
    }
}
