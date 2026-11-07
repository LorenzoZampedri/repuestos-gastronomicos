package com.repuestos.services;

import com.repuestos.models.Cliente;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FotoPerfilServiceRollbackTest {

    @TempDir
    Path tempDir;

    private AvatarStorageService avatarStorageService;
    private UsuarioRepository usuarioRepository;
    private ClienteRepository clienteRepository;
    private AuditoriaService auditoriaService;
    private FotoPerfilService fotoPerfilService;

    @BeforeEach
    void setUp() {
        avatarStorageService = new AvatarStorageService(tempDir.toString());
        usuarioRepository = mock(UsuarioRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        auditoriaService = mock(AuditoriaService.class);
        fotoPerfilService = new FotoPerfilService(
                avatarStorageService, usuarioRepository, clienteRepository, auditoriaService);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void siFallaLaContraparteElArchivoNuevoSeBorraYElViejoSeConserva() throws IOException {
        Path viejo = archivoExistente();
        String viejaUrl = "/api/avatares/" + viejo.getFileName();

        Usuario usuario = Usuario.builder().id(1L).username("cliente")
                .nombre("Cliente").rol(Usuario.Rol.CLIENTE)
                .fotoUrl(viejaUrl).build();
        Cliente cliente = Cliente.builder().id(2L).nombre("Cliente").fotoUrl(viejaUrl).build();
        usuario.setCliente(cliente);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(clienteRepository.save(any())).thenThrow(new RuntimeException("falla simulada"));

        assertThrows(RuntimeException.class,
                () -> fotoPerfilService.cambiarFotoUsuario(1L, foto()));

        verificarRollback();
        assertTrue(Files.exists(viejo), "el archivo anterior debe conservarse");
        assertTrue(archivosEn(tempDir) == 1, "el archivo nuevo debe haberse borrado");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void siFallaLaActualizacionDelUsuarioElArchivoNuevoSeBorra() throws IOException {
        Path viejo = archivoExistente();
        String viejaUrl = "/api/avatares/" + viejo.getFileName();

        Usuario usuario = Usuario.builder().id(1L).username("cliente")
                .nombre("Cliente").rol(Usuario.Rol.CLIENTE)
                .fotoUrl(viejaUrl).build();
        Cliente cliente = Cliente.builder().id(2L).nombre("Cliente").fotoUrl(viejaUrl).build();
        when(clienteRepository.findById(2L)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findByClienteId(2L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any())).thenThrow(new RuntimeException("falla simulada"));

        assertThrows(RuntimeException.class,
                () -> fotoPerfilService.cambiarFotoCliente(2L, foto()));

        verificarRollback();
        assertTrue(Files.exists(viejo), "el archivo anterior debe conservarse");
        assertTrue(archivosEn(tempDir) == 1, "el archivo nuevo debe haberse borrado");
        verify(clienteRepository, never()).save(any());
    }

    private void verificarRollback() {
        List<TransactionSynchronization> sincronizaciones =
                TransactionSynchronizationManager.getSynchronizations();
        sincronizaciones.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private Path archivoExistente() throws IOException {
        String nombre = UUID.randomUUID() + ".jpg";
        Path archivo = tempDir.resolve(nombre);
        Files.write(archivo, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        return archivo;
    }

    private MockMultipartFile foto() throws IOException {
        BufferedImage imagen = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        try {
            g.setColor(Color.GREEN);
            g.fillRect(0, 0, 100, 100);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", out);
        return new MockMultipartFile("file", "foto.jpg", "image/jpeg", out.toByteArray());
    }

    private long archivosEn(Path directorio) throws IOException {
        try (var stream = Files.list(directorio)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }

}
