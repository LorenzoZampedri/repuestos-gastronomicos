package com.repuestos.config;

import com.repuestos.models.Categoria;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.CategoriaRepository;
import com.repuestos.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UsuarioRepository usuarioRepository, CategoriaRepository categoriaRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.categoriaRepository = categoriaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            if (!usuarioRepository.existsByUsername("admin")) {
                Usuario admin = Usuario.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .nombre("Administrador")
                    .email("admin@repuestos.com")
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
    }
}
