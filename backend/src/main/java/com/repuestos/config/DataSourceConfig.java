package com.repuestos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DataSourceConfig {

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Value("${SPRING_DATASOURCE_URL:}")
    private String springDatasourceUrl;

    @Value("${SPRING_DATASOURCE_USERNAME:}")
    private String springDatasourceUsername;

    @Value("${SPRING_DATASOURCE_PASSWORD:}")
    private String springDatasourcePassword;

    @Bean
    public DataSource dataSource() throws URISyntaxException {
        if (springDatasourceUrl != null && !springDatasourceUrl.isEmpty()) {
            return DataSourceBuilder.create()
                    .url(springDatasourceUrl)
                    .username(springDatasourceUsername == null || springDatasourceUsername.isEmpty() ? "admin" : springDatasourceUsername)
                    .password(springDatasourcePassword == null || springDatasourcePassword.isEmpty() ? "repuestos123" : springDatasourcePassword)
                    .driverClassName("org.postgresql.Driver")
                    .build();
        }

        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            URI uri = new URI(databaseUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            String userInfo = uri.getUserInfo();

            String dbName = (path != null && path.startsWith("/")) ? path.substring(1) : "postgres";
            String username = "";
            String password = "";
            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts[1];
            }

            String jdbcUrl = "jdbc:postgresql://" + host + (port > 0 ? ":" + port : "") + "/" + dbName;
            return DataSourceBuilder.create()
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();
        }

        return DataSourceBuilder.create()
                .url("jdbc:postgresql://localhost:5432/repuestos_db")
                .username("admin")
                .password("repuestos123")
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
