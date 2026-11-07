package com.repuestos.config;

import com.repuestos.models.Cliente;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.services.FotoPerfilService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class ClienteUsuarioMigration implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final FotoPerfilService fotoPerfilService;

    @Override
    @Transactional
    public void run(String... args) {
        List<Usuario> clientesSinVincular =
                usuarioRepository.findByRolAndClienteIsNull(Usuario.Rol.CLIENTE);
        if (clientesSinVincular.isEmpty()) {
            log.info("Migración de clientes: no hay usuarios CLIENTE sin vincular");
            return;
        }

        for (Usuario usuario : clientesSinVincular) {
            Cliente cliente = clienteRepository.save(Cliente.builder()
                    .nombre(usuario.getNombre())
                    .email(usuario.getEmail())
                    .observaciones("Creado automáticamente desde el registro web")
                    .saldoCorriente(BigDecimal.ZERO)
                    .activo(true)
                    .build());
            usuario.setCliente(cliente);
            usuarioRepository.save(usuario);
            fotoPerfilService.reconciliarVinculo(usuario, cliente);
        }

        log.info("Migración de clientes: {} usuario(s) CLIENTE quedaron vinculados a un Cliente",
                clientesSinVincular.size());
    }
}
