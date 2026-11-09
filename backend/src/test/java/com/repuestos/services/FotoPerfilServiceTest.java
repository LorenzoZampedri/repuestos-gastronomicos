package com.repuestos.services;

import com.repuestos.config.ClienteUsuarioMigration;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Cliente;
import com.repuestos.models.LogActividad;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ClienteService.class, CargoCuentaCorrienteService.class, VentaService.class,
        ProductoService.class, ConfiguracionService.class, AuditoriaService.class,
        FotoPerfilService.class, AvatarStorageService.class})
class FotoPerfilServiceTest {

    private static final Path AVATAR_DIR = Path.of("target", "test-avatares");

    @Autowired private FotoPerfilService fotoPerfilService;
    @Autowired private ClienteService clienteService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private LogActividadRepository logActividadRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() throws IOException {
        tx = new TransactionTemplate(transactionManager);
        SecurityContextHolder.clearContext();
        logActividadRepository.deleteAll();
        usuarioRepository.deleteAll();
        clienteRepository.deleteAll();
        borrarAvatares();
    }

    @AfterEach
    void tearDown() throws IOException {
        SecurityContextHolder.clearContext();
        logActividadRepository.deleteAll();
        usuarioRepository.deleteAll();
        clienteRepository.deleteAll();
        borrarAvatares();
    }

    // ------------------------------------------------------------------
    // Independencia sin vínculo
    // ------------------------------------------------------------------

    @Test
    void usuarioSinClienteNoTocaNingunCliente() throws IOException {
        Usuario admin = crearUsuario("admin-solo", Usuario.Rol.ADMIN);
        Cliente clienteSuelto = crearCliente("Cliente Suelto");
        autenticar("admin-solo", "ADMIN");

        fotoPerfilService.cambiarFotoUsuario(admin.getId(), foto());

        assertNotNull(fotoUsuario(admin.getId()));
        assertNull(fotoCliente(clienteSuelto.getId()));
        assertEquals(1, archivosAvatar());
    }

    @Test
    void clienteSinCuentaNoTocaNingunUsuario() throws IOException {
        Usuario admin = crearUsuario("admin-control", Usuario.Rol.ADMIN);
        Cliente clienteSuelto = crearCliente("Cliente Mostrador");
        autenticar("admin-control", "ADMIN");

        fotoPerfilService.cambiarFotoCliente(clienteSuelto.getId(), foto());

        assertNotNull(fotoCliente(clienteSuelto.getId()));
        assertNull(fotoUsuario(admin.getId()));
        assertEquals(1, archivosAvatar());
    }

    @Test
    void quitarFotoDeUsuarioSinVinculoNoTocaClientes() throws IOException {
        Usuario admin = crearUsuario("admin-quitar", Usuario.Rol.ADMIN);
        Cliente clienteSuelto = crearCliente("Cliente Independiente");
        autenticar("admin-quitar", "ADMIN");
        fotoPerfilService.cambiarFotoUsuario(admin.getId(), foto());
        String url = fotoUsuario(admin.getId());

        fotoPerfilService.quitarFotoUsuario(admin.getId());

        assertNull(fotoUsuario(admin.getId()));
        assertNull(fotoCliente(clienteSuelto.getId()));
        assertFalse(Files.exists(archivo(url)));
    }

    // ------------------------------------------------------------------
    // Sincronización con vínculo
    // ------------------------------------------------------------------

    @Test
    void clienteRegistradoComparteSuFotoConLaFicha() throws IOException {
        Usuario usuario = crearUsuarioCliente("cliente-registro");
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        autenticar(usuario.getUsername(), "CLIENTE");

        fotoPerfilService.cambiarFotoUsuario(usuario.getId(), foto());

        String url = fotoUsuario(usuario.getId());
        assertNotNull(url);
        assertEquals(url, fotoCliente(cliente.getId()));
        assertEquals(1, archivosAvatar(), "un par vinculado debe tener un solo archivo");
        assertEquals(0, logActividadRepository.count(),
                "un CLIENTE no genera auditoría por su propia foto");
    }

    @Test
    void adminCambiaFotoDeUsuarioVinculadoYSincronizaLaFicha() throws IOException {
        Usuario usuario = crearUsuarioCliente("cliente-admin");
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        crearUsuario("admin-foto", Usuario.Rol.ADMIN);
        autenticar("admin-foto", "ADMIN");

        fotoPerfilService.cambiarFotoUsuario(usuario.getId(), foto());

        String url = fotoUsuario(usuario.getId());
        assertEquals(url, fotoCliente(cliente.getId()));
        assertEquals(1, logActividadRepository.count());
        LogActividad log = logActividadRepository.findAll().get(0);
        assertEquals(AccionAuditoria.USUARIO_FOTO_CAMBIADA.name(), log.getAccion());
        assertTrue(log.getDetalle().contains("sincronizadoCon"));
    }

    @Test
    void empleadoCambiaFotoDeLaFichaYSincronizaLaCuenta() throws IOException {
        Usuario usuario = crearUsuarioCliente("cliente-empleado");
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        crearUsuario("empleado-foto", Usuario.Rol.EMPLEADO);
        autenticar("empleado-foto", "EMPLEADO");

        fotoPerfilService.cambiarFotoCliente(cliente.getId(), foto());

        String url = fotoCliente(cliente.getId());
        assertEquals(url, fotoUsuario(usuario.getId()));
        assertEquals(1, archivosAvatar());
        assertEquals(1, logActividadRepository.count());
        LogActividad log = logActividadRepository.findAll().get(0);
        assertEquals(AccionAuditoria.CLIENTE_FOTO_CAMBIADA.name(), log.getAccion());
        assertTrue(log.getDetalle().contains("sincronizadoCon"));
    }

    @Test
    void quitarFotoDesdeUsuarioVinculadoLimpiaAmbosYLiberaElArchivo() throws IOException {
        Usuario usuario = crearUsuarioCliente("cliente-quitar");
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        crearUsuario("admin-quitar-par", Usuario.Rol.ADMIN);
        autenticar("admin-quitar-par", "ADMIN");
        fotoPerfilService.cambiarFotoUsuario(usuario.getId(), foto());
        String url = fotoUsuario(usuario.getId());

        fotoPerfilService.quitarFotoUsuario(usuario.getId());

        assertNull(fotoUsuario(usuario.getId()));
        assertNull(fotoCliente(cliente.getId()));
        assertFalse(Files.exists(archivo(url)));
        assertEquals(2, logActividadRepository.count(),
                "una fila por cada acción, no una por entidad sincronizada");
    }

    @Test
    void quitarFotoDesdeClienteVinculadoLimpiaAmbos() throws IOException {
        Usuario usuario = crearUsuarioCliente("cliente-quitar-ficha");
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        crearUsuario("admin-quitar-ficha", Usuario.Rol.ADMIN);
        autenticar("admin-quitar-ficha", "ADMIN");
        fotoPerfilService.cambiarFotoCliente(cliente.getId(), foto());
        String url = fotoCliente(cliente.getId());

        fotoPerfilService.quitarFotoCliente(cliente.getId());

        assertNull(fotoCliente(cliente.getId()));
        assertNull(fotoUsuario(usuario.getId()));
        assertFalse(Files.exists(archivo(url)));
    }

    @Test
    void reemplazarFotoDeUnParBorraElArchivoViejoUnaSolaVez() throws IOException {
        Usuario usuario = crearUsuarioCliente("cliente-reemplazo");
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        crearUsuario("admin-reemplazo", Usuario.Rol.ADMIN);
        autenticar("admin-reemplazo", "ADMIN");
        fotoPerfilService.cambiarFotoUsuario(usuario.getId(), foto());
        String vieja = fotoUsuario(usuario.getId());

        fotoPerfilService.cambiarFotoCliente(cliente.getId(), foto());

        String nueva = fotoCliente(cliente.getId());
        assertEquals(nueva, fotoUsuario(usuario.getId()));
        assertFalse(Files.exists(archivo(vieja)));
        assertTrue(Files.exists(archivo(nueva)));
        assertEquals(1, archivosAvatar());
    }

    // ------------------------------------------------------------------
    // Certeza del vínculo
    // ------------------------------------------------------------------

    @Test
    void clienteConMismosDatosPeroSinVinculoNoSeSincroniza() throws IOException {
        Usuario usuario = crearUsuarioCliente("homónimo");
        Cliente suelto = crearCliente("homónimo");
        suelto.setEmail(usuario.getEmail());
        clienteRepository.save(suelto);
        autenticar("homónimo", "CLIENTE");

        fotoPerfilService.cambiarFotoUsuario(usuario.getId(), foto());

        assertNotNull(fotoUsuario(usuario.getId()));
        assertNull(fotoCliente(suelto.getId()));
    }

    // ------------------------------------------------------------------
    // Reconciliación al crear o cambiar el vínculo
    // ------------------------------------------------------------------

    @Test
    void alCrearLaFichaElClienteRecibeLaFotoDelUsuario() throws IOException {
        Usuario usuario = crearUsuarioCliente("reconciliar-creacion");
        autenticar(usuario.getUsername(), "CLIENTE");
        fotoPerfilService.cambiarFotoUsuario(usuario.getId(), foto());
        String url = fotoUsuario(usuario.getId());
        Usuario recargado = usuarioRepository.findById(usuario.getId()).orElseThrow();

        Cliente cliente = clienteService.asegurarClienteParaUsuario(recargado);

        assertEquals(url, fotoCliente(cliente.getId()));
    }

    @Test
    void alVincularUnClienteConFotoElUsuarioLaRecibe() throws IOException {
        Usuario usuario = crearUsuarioCliente("reconciliar-cliente");
        Cliente anterior = clienteService.asegurarClienteParaUsuario(usuario);
        Cliente destino = crearCliente("Destino Con Foto");
        crearUsuario("admin-reconciliar", Usuario.Rol.ADMIN);
        autenticar("admin-reconciliar", "ADMIN");
        fotoPerfilService.cambiarFotoCliente(destino.getId(), foto());
        String url = fotoCliente(destino.getId());

        clienteService.vincularClienteAUsuario(usuario.getId(), destino.getId());

        assertEquals(url, fotoUsuario(usuario.getId()));
        assertEquals(url, fotoCliente(destino.getId()));
        assertNull(fotoCliente(anterior.getId()));
    }

    @Test
    void siAmbosTienenFotoDistintaGanaLaDelUsuarioYSeLiberaLaDelCliente() throws IOException {
        Usuario usuario = crearUsuarioCliente("reconciliar-gana");
        Cliente anterior = clienteService.asegurarClienteParaUsuario(usuario);
        Cliente destino = crearCliente("Destino Con Otra Foto");
        crearUsuario("admin-reconciliar-2", Usuario.Rol.ADMIN);
        autenticar("admin-reconciliar-2", "ADMIN");
        fotoPerfilService.cambiarFotoUsuario(usuario.getId(), foto());
        fotoPerfilService.cambiarFotoCliente(destino.getId(), foto());
        String urlUsuario = fotoUsuario(usuario.getId());
        String urlDestino = fotoCliente(destino.getId());
        assertFalse(urlUsuario.equals(urlDestino));

        clienteService.vincularClienteAUsuario(usuario.getId(), destino.getId());

        assertEquals(urlUsuario, fotoUsuario(usuario.getId()));
        assertEquals(urlUsuario, fotoCliente(destino.getId()));
        assertFalse(Files.exists(archivo(urlDestino)));
        assertTrue(Files.exists(archivo(urlUsuario)));
        assertEquals(urlUsuario, fotoCliente(anterior.getId()));
    }

    @Test
    void alRevincularElClienteAnteriorConservaSuFotoSiSigueReferenciada() throws IOException {
        Usuario usuario = crearUsuarioCliente("revincular");
        Cliente anterior = clienteService.asegurarClienteParaUsuario(usuario);
        crearUsuario("admin-revincular", Usuario.Rol.ADMIN);
        autenticar("admin-revincular", "ADMIN");
        fotoPerfilService.cambiarFotoUsuario(usuario.getId(), foto());
        String url = fotoUsuario(usuario.getId());
        Cliente destino = crearCliente("Destino Sin Foto");

        clienteService.vincularClienteAUsuario(usuario.getId(), destino.getId());

        assertEquals(url, fotoUsuario(usuario.getId()));
        assertEquals(url, fotoCliente(destino.getId()));
        assertEquals(url, fotoCliente(anterior.getId()),
                "el cliente anterior conserva la URL que tenía");
        assertTrue(Files.exists(archivo(url)),
                "el archivo no se borra mientras alguien lo referencie");
    }

    @Test
    void laMigracionReconciliaLaFotoDeCuentasYaVinculadas() throws IOException {
        Usuario usuario = crearUsuarioCliente("migracion-foto");
        fotoPerfilService.cambiarFotoUsuario(usuario.getId(), foto());
        String url = fotoUsuario(usuario.getId());
        ClienteUsuarioMigration migration = new ClienteUsuarioMigration(
                usuarioRepository, clienteRepository, fotoPerfilService);

        migration.run();

        Usuario recargado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertNotNull(recargado.getCliente());
        assertEquals(url, fotoCliente(recargado.getCliente().getId()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Usuario crearUsuario(String username, Usuario.Rol rol) {
        return usuarioRepository.save(Usuario.builder()
                .username(username)
                .passwordHash("hash")
                .nombre("Usuario " + username)
                .email(username + "@example.com")
                .rol(rol)
                .activo(true)
                .build());
    }

    private Usuario crearUsuarioCliente(String username) {
        return crearUsuario(username, Usuario.Rol.CLIENTE);
    }

    private Cliente crearCliente(String nombre) {
        return clienteRepository.save(Cliente.builder()
                .nombre(nombre)
                .saldoCorriente(java.math.BigDecimal.ZERO)
                .activo(true)
                .build());
    }

    private void autenticar(String username, String rol) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x",
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private MockMultipartFile foto() throws IOException {
        BufferedImage imagen = new BufferedImage(200, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        try {
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, 200, 160);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", out);
        return new MockMultipartFile("file", "foto.jpg", "image/jpeg", out.toByteArray());
    }

    private String fotoUsuario(Long id) {
        return tx.execute(status -> {
            Usuario usuario = entityManager.find(Usuario.class, id);
            return usuario == null ? null : usuario.getFotoUrl();
        });
    }

    private String fotoCliente(Long id) {
        return tx.execute(status -> {
            Cliente cliente = entityManager.find(Cliente.class, id);
            return cliente == null ? null : cliente.getFotoUrl();
        });
    }

    private Path archivo(String url) {
        return AVATAR_DIR.resolve(url.substring("/api/avatares/".length()));
    }

    private long archivosAvatar() throws IOException {
        if (!Files.exists(AVATAR_DIR)) {
            return 0;
        }
        try (var stream = Files.list(AVATAR_DIR)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }

    private void borrarAvatares() throws IOException {
        if (!Files.exists(AVATAR_DIR)) {
            return;
        }
        try (var stream = Files.walk(AVATAR_DIR)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best effort
                        }
                    });
        }
    }
}
