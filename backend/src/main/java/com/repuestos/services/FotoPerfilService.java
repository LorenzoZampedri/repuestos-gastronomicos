package com.repuestos.services;

import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Cliente;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FotoPerfilService {

    private final AvatarStorageService avatarStorageService;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    @Transactional
    public void reconciliarVinculo(Usuario usuario, Cliente cliente) {
        if (usuario == null || cliente == null) {
            return;
        }
        String uFoto = usuario.getFotoUrl();
        String cFoto = cliente.getFotoUrl();

        if (uFoto != null && cFoto != null) {
            if (!uFoto.equals(cFoto)) {
                // Gana la del usuario; cliente adopta la del usuario y la vieja de cliente se evalúa para limpiar
                cliente.setFotoUrl(uFoto);
                clienteRepository.save(cliente);
                programarLimpiezaSiHuerfana(cFoto);
            }
        } else if (uFoto != null) {
            cliente.setFotoUrl(uFoto);
            clienteRepository.save(cliente);
        } else if (cFoto != null) {
            usuario.setFotoUrl(cFoto);
            usuarioRepository.save(usuario);
        }
    }

    @Transactional
    public Usuario cambiarFotoUsuario(Long usuarioId, MultipartFile file) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String nuevaUrl = avatarStorageService.guardar(file);
        programarLimpiezaEnRollback(nuevaUrl);
        String viejaUrl = usuario.getFotoUrl();

        usuario.setFotoUrl(nuevaUrl);
        boolean sincronizado = false;
        String contraparteNombre = null;

        if (usuario.getCliente() != null) {
            Cliente cliente = usuario.getCliente();
            cliente.setFotoUrl(nuevaUrl);
            clienteRepository.save(cliente);
            sincronizado = true;
            contraparteNombre = cliente.getNombre();
        }

        Usuario guardado = usuarioRepository.save(usuario);

        if (viejaUrl != null && !viejaUrl.equals(nuevaUrl)) {
            programarLimpiezaSiHuerfana(viejaUrl);
        }

        // Auditoría
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("usuarioId", guardado.getId());
        detalle.put("username", guardado.getUsername());
        if (sincronizado) {
            detalle.put("sincronizadoCon", "ficha de cliente '" + contraparteNombre + "'");
        }
        String resumen = "Cambió la foto del usuario '" + guardado.getUsername() + "'";
        auditoriaService.registrar(AccionAuditoria.USUARIO_FOTO_CAMBIADA, "Usuario", guardado.getId(), resumen, detalle);

        return guardado;
    }

    @Transactional
    public Usuario quitarFotoUsuario(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String viejaUrl = usuario.getFotoUrl();
        if (viejaUrl == null) {
            return usuario;
        }

        usuario.setFotoUrl(null);
        boolean sincronizado = false;
        String contraparteNombre = null;

        if (usuario.getCliente() != null) {
            Cliente cliente = usuario.getCliente();
            cliente.setFotoUrl(null);
            clienteRepository.save(cliente);
            sincronizado = true;
            contraparteNombre = cliente.getNombre();
        }

        Usuario guardado = usuarioRepository.save(usuario);
        programarLimpiezaSiHuerfana(viejaUrl);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("usuarioId", guardado.getId());
        detalle.put("username", guardado.getUsername());
        if (sincronizado) {
            detalle.put("sincronizadoCon", "ficha de cliente '" + contraparteNombre + "'");
        }
        String resumen = "Eliminó la foto del usuario '" + guardado.getUsername() + "'";
        auditoriaService.registrar(AccionAuditoria.USUARIO_FOTO_ELIMINADA, "Usuario", guardado.getId(), resumen, detalle);

        return guardado;
    }

    @Transactional
    public Cliente cambiarFotoCliente(Long clienteId, MultipartFile file) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        String nuevaUrl = avatarStorageService.guardar(file);
        programarLimpiezaEnRollback(nuevaUrl);
        String viejaUrl = cliente.getFotoUrl();

        cliente.setFotoUrl(nuevaUrl);
        boolean sincronizado = false;
        String contraparteUsername = null;

        Optional<Usuario> usuarioOpt = usuarioRepository.findByClienteId(clienteId);
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            usuario.setFotoUrl(nuevaUrl);
            usuarioRepository.save(usuario);
            sincronizado = true;
            contraparteUsername = usuario.getUsername();
        }

        Cliente guardado = clienteRepository.save(cliente);

        if (viejaUrl != null && !viejaUrl.equals(nuevaUrl)) {
            programarLimpiezaSiHuerfana(viejaUrl);
        }

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clienteId", guardado.getId());
        detalle.put("clienteNombre", guardado.getNombre());
        if (sincronizado) {
            detalle.put("sincronizadoCon", "cuenta de usuario '" + contraparteUsername + "'");
        }
        String resumen = "Cambió la foto del cliente '" + guardado.getNombre() + "'";
        auditoriaService.registrar(AccionAuditoria.CLIENTE_FOTO_CAMBIADA, "Cliente", guardado.getId(), resumen, detalle);

        return guardado;
    }

    @Transactional
    public Cliente quitarFotoCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        String viejaUrl = cliente.getFotoUrl();
        if (viejaUrl == null) {
            return cliente;
        }

        cliente.setFotoUrl(null);
        boolean sincronizado = false;
        String contraparteUsername = null;

        Optional<Usuario> usuarioOpt = usuarioRepository.findByClienteId(clienteId);
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            usuario.setFotoUrl(null);
            usuarioRepository.save(usuario);
            sincronizado = true;
            contraparteUsername = usuario.getUsername();
        }

        Cliente guardado = clienteRepository.save(cliente);
        programarLimpiezaSiHuerfana(viejaUrl);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clienteId", guardado.getId());
        detalle.put("clienteNombre", guardado.getNombre());
        if (sincronizado) {
            detalle.put("sincronizadoCon", "cuenta de usuario '" + contraparteUsername + "'");
        }
        String resumen = "Eliminó la foto del cliente '" + guardado.getNombre() + "'";
        auditoriaService.registrar(AccionAuditoria.CLIENTE_FOTO_ELIMINADA, "Cliente", guardado.getId(), resumen, detalle);

        return guardado;
    }

    private void programarLimpiezaSiHuerfana(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            verificarYEliminar(url);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                verificarYEliminar(url);
            }
        });
    }

    private void verificarYEliminar(String url) {
        long refsUsuarios = usuarioRepository.countByFotoUrl(url);
        long refsClientes = clienteRepository.countByFotoUrl(url);
        if (refsUsuarios == 0 && refsClientes == 0) {
            avatarStorageService.eliminar(url);
        }
    }

    private void programarLimpiezaEnRollback(String url) {
        if (url == null || url.isBlank() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    avatarStorageService.eliminar(url);
                }
            }
        });
    }
}
