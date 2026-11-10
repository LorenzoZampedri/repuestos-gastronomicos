package com.repuestos.services;

import com.repuestos.config.PasswordEncoderConfig;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * En H2 la restricción del entity es sensible a mayúsculas; la garantía real
 * sobre LOWER(username) se verifica manualmente contra PostgreSQL. Aun así, dos
 * altas simultáneas con el mismo username exacto deben dejar una sola cuenta y
 * un 409 específico en la otra, nunca un 500 ni el mensaje genérico.
 */
@DataJpaTest
@Import({UsuarioService.class, ClienteService.class, CargoCuentaCorrienteService.class,
        VentaService.class, ProductoService.class, ConfiguracionService.class,
        AuditoriaService.class, PasswordEncoderConfig.class, FotoPerfilService.class,
        AvatarStorageService.class})
class UsuarioConcurrenciaTest {

    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosAltasSimultaneasConElMismoUsernameSoloCreanUnaCuenta() throws Exception {
        String username = "carrera-" + System.nanoTime();
        int hilos = 2;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Object>> resultados = new ArrayList<>();

        try {
            for (int i = 0; i < hilos; i++) {
                int indice = i;
                resultados.add(pool.submit(() -> {
                    listos.countDown();
                    salida.await();
                    try {
                        return usuarioService.registrar(Usuario.builder()
                                .username(username)
                                .passwordHash("clave-segura")
                                .nombre("Carrera " + indice)
                                .rol(Usuario.Rol.EMPLEADO)
                                .activo(true)
                                .build());
                    } catch (NegocioException error) {
                        return error;
                    }
                }));
            }

            listos.await();
            salida.countDown();

            int creadas = 0;
            int conflictos = 0;
            for (Future<Object> resultado : resultados) {
                Object valor = resultado.get();
                if (valor instanceof Usuario) {
                    creadas++;
                } else {
                    NegocioException error = assertInstanceOf(NegocioException.class, valor);
                    assertEquals(HttpStatus.CONFLICT, error.getStatus());
                    assertEquals(UsuarioUnicidad.CODE_USERNAME_DUPLICADO, error.getCode());
                    conflictos++;
                }
            }

            assertEquals(1, creadas);
            assertEquals(1, conflictos);
            assertEquals(1, usuarioRepository.findAll().stream()
                    .filter(usuario -> username.equalsIgnoreCase(usuario.getUsername()))
                    .count());
        } finally {
            pool.shutdownNow();
            usuarioRepository.findByUsernameIgnoreCase(username)
                    .ifPresent(usuarioRepository::delete);
        }
    }
}
