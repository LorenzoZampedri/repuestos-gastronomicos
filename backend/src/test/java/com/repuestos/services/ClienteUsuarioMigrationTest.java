package com.repuestos.services;

import com.repuestos.config.ClienteUsuarioMigration;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@Import({FotoPerfilService.class, AvatarStorageService.class, AuditoriaService.class})
class ClienteUsuarioMigrationTest {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private FotoPerfilService fotoPerfilService;

    @Test
    void vinculaClientesExistentesSinDuplicarAlReejecutar() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("migracion-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Cliente Migración")
                .email("migracion@example.com")
                .rol(Usuario.Rol.CLIENTE)
                .activo(true)
                .build());
        ClienteUsuarioMigration migration =
                new ClienteUsuarioMigration(usuarioRepository, clienteRepository, fotoPerfilService);

        migration.run();
        migration.run();

        Usuario recargado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertNotNull(recargado.getCliente());
        assertEquals("Cliente Migración", recargado.getCliente().getNombre());
        assertEquals(1, clienteRepository.count());
    }
}
