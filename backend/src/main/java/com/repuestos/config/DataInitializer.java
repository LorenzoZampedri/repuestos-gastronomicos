package com.repuestos.config;

import com.repuestos.models.Categoria;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.CategoriaRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.services.ConfiguracionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConfiguracionService configuracionService;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    public DataInitializer(UsuarioRepository usuarioRepository, CategoriaRepository categoriaRepository, PasswordEncoder passwordEncoder, ConfiguracionService configuracionService) {
        this.usuarioRepository = usuarioRepository;
        this.categoriaRepository = categoriaRepository;
        this.passwordEncoder = passwordEncoder;
        this.configuracionService = configuracionService;
    }

    @Override
    public void run(String... args) {
        try {
            if (!usuarioRepository.existsByUsernameIgnoreCase("admin")) {
                String password = (adminPassword == null || adminPassword.isBlank())
                        ? "admin123"
                        : adminPassword;
                if (adminPassword == null || adminPassword.isBlank()) {
                    log.warn("ADMIN_PASSWORD no está definida. Se creó el usuario admin con la "
                            + "contraseña por defecto 'admin123'. Cambiala antes de usar en producción.");
                }
                String emailAdmin = "admin@repuestos.com";
                if (usuarioRepository.existsByEmailIgnoreCase(emailAdmin)) {
                    log.warn("El email {} ya está en uso; el usuario admin se creará sin email",
                            emailAdmin);
                    emailAdmin = null;
                }
                Usuario admin = Usuario.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode(password))
                    .nombre("Administrador")
                    .email(emailAdmin)
                    .rol(Usuario.Rol.ADMIN)
                    .activo(true)
                    .build();
                usuarioRepository.save(admin);
                log.info("Usuario admin creado exitosamente");
            } else {
                log.info("Usuario admin ya existe");
            }
        } catch (Exception e) {
            log.error("Error al crear usuario admin: {}", e.getMessage());
        }

        try {
            if (categoriaRepository.count() == 0) {
                String[][] categorias = {
                    {"Hornos Pizzeros", "Hornos para preparación de pizzas"},
                    {"Freidoras", "Freidoras industriales para gastronomía"},
                    {"Anafes", "Anafes y cocinas industriales"},
                    {"Cafeteras", "Cafeteras de bar y uso comercial"},
                    {"Refrigeración", "Equipos de refrigeración industrial"},
                    {"Lavado", "Lavavajillas y equipos de limpieza"},
                    {"Utensilios", "Utensilios y herramientas de cocina"},
                    {"Repuestos", "Repuestos y componentes varios"}
                };
                for (String[] cat : categorias) {
                    categoriaRepository.save(Categoria.builder()
                        .nombre(cat[0])
                        .descripcion(cat[1])
                        .build());
                }
                log.info("Categorías creadas exitosamente");
            }
        } catch (Exception e) {
            log.error("Error al crear categorías: {}", e.getMessage());
        }

        try {
            configuracionService.inicializarClaves();
            configuracionService.migrarPreciosUsd();
        } catch (Exception e) {
            log.error("Error al inicializar configuración: {}", e.getMessage());
        }
    }
}
