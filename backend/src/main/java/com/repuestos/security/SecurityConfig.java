package com.repuestos.security;

import com.repuestos.services.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuditoriaService auditoriaService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Público
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/productos/catalogo").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categorias").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/configuracion/publica").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/uploads/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Autenticado (cualquier rol)
                .requestMatchers(HttpMethod.PUT, "/api/auth/password").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/auth/foto").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/auth/foto").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/avatares/**").authenticated()

                // Solo CLIENTE: crear pedidos y consultar/cancelar los propios
                .requestMatchers(HttpMethod.POST, "/api/pedidos").hasRole("CLIENTE")
                .requestMatchers("/api/pedidos/mios", "/api/pedidos/mios/**").hasRole("CLIENTE")

                // ADMIN y EMPLEADO: gestión de pedidos
                .requestMatchers("/api/pedidos/**").hasAnyRole("ADMIN", "EMPLEADO")

                // Solo ADMIN: rutas sensibles
                .requestMatchers("/api/usuarios/**", "/api/dashboard/**").hasRole("ADMIN")
                .requestMatchers("/api/precios/**").hasRole("ADMIN")
                .requestMatchers("/api/logs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/clientes/*/foto").hasAnyRole("ADMIN", "EMPLEADO")
                .requestMatchers(HttpMethod.DELETE, "/api/clientes/*/foto").hasAnyRole("ADMIN", "EMPLEADO")
                .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/clientes/deudores").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/clientes/*/pagos").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/clientes/*/saldo").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/ventas/pendientes").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/ventas/cliente/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/ventas/resumen").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/ventas/*/anular").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/configuracion", "/api/configuracion/historial").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/configuracion/**").hasRole("ADMIN")

                // ADMIN y EMPLEADO: operación diaria
                .requestMatchers(HttpMethod.POST, "/api/categorias").hasAnyRole("ADMIN", "EMPLEADO")
                .requestMatchers("/api/productos/**").hasAnyRole("ADMIN", "EMPLEADO")
                .requestMatchers("/api/clientes/**").hasAnyRole("ADMIN", "EMPLEADO")
                .requestMatchers("/api/ventas/**").hasAnyRole("ADMIN", "EMPLEADO")

                // Todo lo demás queda cerrado
                .anyRequest().denyAll()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"No autenticado o sesión expirada\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    auditoriaService.registrarAccesoDenegado(
                            request.getMethod(), request.getRequestURI());
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"No tenés permisos para realizar esta acción\"}");
                })
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
