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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Crea los índices únicos funcionales de username y email. Si ya existen datos
 * duplicados, no crea el índice afectado ni frena el arranque: deja un WARN con
 * los casos para resolverlos a mano. Es idempotente y solo aplica a PostgreSQL.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class UsuarioUnicidadMigration implements CommandLineRunner {

    private static final String SQL_DUPLICADOS_USERNAME = """
            SELECT id, username FROM usuarios
            WHERE LOWER(username) IN (
                SELECT LOWER(username) FROM usuarios
                GROUP BY LOWER(username) HAVING COUNT(*) > 1
            )
            ORDER BY LOWER(username), id
            """;

    private static final String SQL_DUPLICADOS_EMAIL = """
            SELECT id, email FROM usuarios
            WHERE email IS NOT NULL AND email <> ''
              AND LOWER(email) IN (
                  SELECT LOWER(email) FROM usuarios
                  WHERE email IS NOT NULL AND email <> ''
                  GROUP BY LOWER(email) HAVING COUNT(*) > 1
              )
            ORDER BY LOWER(email), id
            """;

    private static final String INDICE_USERNAME =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_username_lower "
                    + "ON usuarios (LOWER(username))";

    private static final String INDICE_EMAIL =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_email_lower "
                    + "ON usuarios (LOWER(email)) WHERE email IS NOT NULL AND email <> ''";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        if (!esPostgres()) {
            log.info("Migración de unicidad de usuarios omitida: solo aplica a PostgreSQL");
            return;
        }

        crearIndiceSiNoHayDuplicados("username", detectarDuplicadosUsername(),
                INDICE_USERNAME);
        crearIndiceSiNoHayDuplicados("email", detectarDuplicadosEmail(), INDICE_EMAIL);
    }

    List<Duplicado> detectarDuplicadosUsername() {
        return jdbcTemplate.query(SQL_DUPLICADOS_USERNAME,
                (rs, fila) -> new Duplicado(rs.getLong("id"), rs.getString("username")));
    }

    List<Duplicado> detectarDuplicadosEmail() {
        return jdbcTemplate.query(SQL_DUPLICADOS_EMAIL,
                (rs, fila) -> new Duplicado(rs.getLong("id"), rs.getString("email")));
    }

    private void crearIndiceSiNoHayDuplicados(String campo, List<Duplicado> duplicados,
                                              String sentencia) {
        if (!duplicados.isEmpty()) {
            log.warn("No se creó el índice único de {}: hay {} fila(s) duplicada(s) que "
                            + "deben resolverse a mano. Casos: {}",
                    campo, duplicados.size(), formatear(duplicados));
            return;
        }
        try {
            jdbcTemplate.execute(sentencia);
            log.info("Índice único de {} verificado", campo);
        } catch (RuntimeException ex) {
            log.error("No se pudo crear el índice único de {}: {}", campo, ex.getMessage());
        }
    }

    private String formatear(List<Duplicado> duplicados) {
        return duplicados.stream()
                .map(duplicado -> "#" + duplicado.id() + "='" + duplicado.valor() + "'")
                .collect(Collectors.joining(", "));
    }

    private boolean esPostgres() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(
                    connection.getMetaData().getDatabaseProductName());
        }
    }

    record Duplicado(Long id, String valor) {
    }
}
