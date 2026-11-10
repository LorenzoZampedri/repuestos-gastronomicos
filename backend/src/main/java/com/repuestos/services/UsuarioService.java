package com.repuestos.services;

import com.repuestos.dto.UsuarioActualizarRequest;
import com.repuestos.dto.UsuarioCrearRequest;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClienteService clienteService;
    private final AuditoriaService auditoriaService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = buscarPorUsername(username);

        return User.withUsername(usuario.getUsername())
                .password(usuario.getPasswordHash())
                .disabled(!Boolean.TRUE.equals(usuario.getActivo()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))
                .build();
    }

    @Transactional
    public Usuario registrar(Usuario usuario) {
        if (usuario == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El usuario es obligatorio");
        }

        String username = normalizarUsername(usuario.getUsername());
        if (username == null || username.isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El nombre de usuario es obligatorio");
        }
        validarFormatoUsernameNuevo(username);
        if (usuarioRepository.existsByUsernameIgnoreCase(username)) {
            throw UsuarioUnicidad.usernameDuplicado();
        }
        if (usuario.getPasswordHash() == null || usuario.getPasswordHash().isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La contraseña es obligatoria");
        }
        if (usuario.getRol() == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El rol es obligatorio");
        }

        usuario.setUsername(username);
        usuario.setNombre(usuario.getNombre() == null ? null : usuario.getNombre().trim());
        usuario.setEmail(normalizarEmail(usuario.getEmail()));
        validarEmailDisponible(usuario.getEmail(), null);
        usuario.setActivo(usuario.getActivo() == null || usuario.getActivo());
        usuario.setPasswordHash(passwordEncoder.encode(usuario.getPasswordHash()));
        Usuario guardado = guardarUsuario(usuario);
        if (guardado.getRol() == Usuario.Rol.CLIENTE) {
            clienteService.asegurarClienteParaUsuario(guardado);
        }

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("usuarioId", guardado.getId());
        detalle.put("username", guardado.getUsername());
        detalle.put("rol", guardado.getRol().name());
        detalle.put("nombre", guardado.getNombre());
        detalle.put("email", guardado.getEmail());
        detalle.put("activo", guardado.getActivo());
        auditoriaService.registrar(AccionAuditoria.USUARIO_CREADO, "Usuario",
                guardado.getId(),
                "Creó el usuario '" + guardado.getUsername() + "' con rol "
                        + guardado.getRol().name(),
                detalle);
        return guardado;
    }

    @Transactional
    public Usuario registrarCliente(String username, String password, String nombre, String email) {
        Usuario usuario = Usuario.builder()
                .username(username)
                .passwordHash(password)
                .nombre(nombre)
                .email(email)
                .rol(Usuario.Rol.CLIENTE)
                .activo(true)
                .build();
        return registrar(usuario);
    }

    @Transactional
    public Usuario crear(UsuarioCrearRequest request) {
        Usuario usuario = Usuario.builder()
                .username(request.username())
                .passwordHash(request.password())
                .nombre(request.nombre())
                .email(request.email())
                .rol(request.rol())
                .activo(true)
                .build();
        return registrar(usuario);
    }

    @Transactional
    public Usuario vincularCliente(Long usuarioId, Long clienteId) {
        Usuario vinculado = clienteService.vincularClienteAUsuario(usuarioId, clienteId);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("usuarioId", vinculado.getId());
        detalle.put("username", vinculado.getUsername());
        detalle.put("clienteId", clienteId);
        detalle.put("clienteNombre", vinculado.getCliente() == null
                ? null : vinculado.getCliente().getNombre());
        auditoriaService.registrar(AccionAuditoria.USUARIO_CLIENTE_VINCULADO, "Usuario",
                vinculado.getId(),
                "Vinculó el usuario '" + vinculado.getUsername() + "' al cliente #" + clienteId,
                detalle);
        return vinculado;
    }

    public List<Usuario> listar(Usuario.Rol rol, Boolean activo) {
        return usuarioRepository.findAllByOrderByIdAsc().stream()
                .filter(usuario -> rol == null || usuario.getRol() == rol)
                .filter(usuario -> activo == null || Boolean.valueOf(activo).equals(usuario.getActivo()))
                .toList();
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    public Usuario buscarPorUsername(String username) {
        String normalizado = normalizarUsername(username);
        return usuarioRepository.findByUsernameIgnoreCase(normalizado)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    @Transactional
    public Usuario actualizar(Long id, UsuarioActualizarRequest request, String usernameActual) {
        Usuario usuario = buscarPorId(id);
        String actor = normalizarActor(usernameActual);
        boolean esMismoUsuario = usuario.getUsername().equalsIgnoreCase(actor);

        if (esMismoUsuario && !Boolean.TRUE.equals(request.activo())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No podés desactivar tu propio usuario");
        }
        if (esMismoUsuario && usuario.getRol() == Usuario.Rol.ADMIN
                && request.rol() != Usuario.Rol.ADMIN) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No podés quitarte el rol ADMIN");
        }
        validarAdminActivo(usuario, request.rol(), request.activo());

        Map<String, Object> antes = datosEditablesUsuario(usuario);

        String email = normalizarEmail(request.email());
        validarEmailDisponible(email, usuario.getId());
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setRol(request.rol());
        usuario.setActivo(request.activo());
        Usuario guardado = guardarUsuario(usuario);
        if (guardado.getRol() == Usuario.Rol.CLIENTE) {
            clienteService.asegurarClienteParaUsuario(guardado);
        }

        Map<String, Object> despues = datosEditablesUsuario(guardado);
        Map<String, Object> cambios = auditoriaService.calcularDiff(antes, despues);
        if (!cambios.isEmpty()) {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("usuarioId", guardado.getId());
            detalle.put("username", guardado.getUsername());
            detalle.put("cambios", cambios);
            auditoriaService.registrar(AccionAuditoria.USUARIO_EDITADO, "Usuario",
                    guardado.getId(),
                    "Editó el usuario '" + guardado.getUsername() + "'",
                    detalle);
        }
        return guardado;
    }

    @Transactional
    public Usuario cambiarActivo(Long id, Boolean activo, String usernameActual) {
        Usuario usuario = buscarPorId(id);
        String actor = normalizarActor(usernameActual);

        if (usuario.getUsername().equalsIgnoreCase(actor) && !Boolean.TRUE.equals(activo)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No podés desactivar tu propio usuario");
        }
        validarAdminActivo(usuario, usuario.getRol(), activo);

        boolean activoSegun = Boolean.TRUE.equals(usuario.getActivo());
        boolean activoNuevo = Boolean.TRUE.equals(activo);
        usuario.setActivo(activo);
        Usuario guardado = usuarioRepository.save(usuario);
        if (activoSegun != activoNuevo) {
            AccionAuditoria accion = activoNuevo
                    ? AccionAuditoria.USUARIO_ACTIVADO
                    : AccionAuditoria.USUARIO_DESACTIVADO;
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("usuarioId", guardado.getId());
            detalle.put("username", guardado.getUsername());
            detalle.put("activoAnterior", activoSegun);
            detalle.put("activoNuevo", activoNuevo);
            auditoriaService.registrar(accion, "Usuario", guardado.getId(),
                    (activoNuevo ? "Activó" : "Desactivó") + " el usuario '"
                            + guardado.getUsername() + "'",
                    detalle);
        }
        return guardado;
    }

    @Transactional
    public void resetPassword(Long id, String passwordNueva) {
        if (passwordNueva == null || passwordNueva.isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La contraseña nueva es obligatoria");
        }
        Usuario usuario = buscarPorId(id);
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(usuario);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("usuarioId", usuario.getId());
        detalle.put("username", usuario.getUsername());
        detalle.put("rol", usuario.getRol() == null ? null : usuario.getRol().name());
        auditoriaService.registrar(AccionAuditoria.PASSWORD_RESETEADA, "Usuario",
                usuario.getId(),
                "Reseteó la contraseña del usuario '" + usuario.getUsername() + "'",
                detalle);
    }

    @Transactional
    public Usuario cambiarPasswordActual(String passwordActual, String passwordNueva) {
        Usuario usuario = verificarPasswordActual(passwordActual);
        if (passwordNueva == null || passwordNueva.isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La contraseña nueva es obligatoria");
        }
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        Usuario guardado = usuarioRepository.save(usuario);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("usuarioId", guardado.getId());
        detalle.put("username", guardado.getUsername());
        detalle.put("rol", guardado.getRol() == null ? null : guardado.getRol().name());
        auditoriaService.registrar(AccionAuditoria.PASSWORD_CAMBIADA, "Usuario",
                guardado.getId(),
                "Cambió su propia contraseña",
                detalle);
        return guardado;
    }

    public Usuario verificarPasswordActual(String password) {
        return verificarPassword(usernameActual(), password);
    }

    public Usuario verificarPassword(String username, String password) {
        Usuario usuario = buscarPorUsername(username);
        if (password == null || password.isBlank()
                || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Contraseña incorrecta");
        }
        return usuario;
    }

    public String usernameActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        return authentication.getName();
    }

    public Usuario usuarioActual() {
        return buscarPorUsername(usernameActual());
    }

    public boolean esAdminActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(autoridad -> "ROLE_ADMIN".equals(autoridad.getAuthority()));
    }

    private void validarAdminActivo(Usuario usuario, Usuario.Rol nuevoRol, Boolean nuevoActivo) {
        boolean eraAdminActivo = usuario.getRol() == Usuario.Rol.ADMIN
                && Boolean.TRUE.equals(usuario.getActivo());
        boolean seguiranAdminActivo = nuevoRol == Usuario.Rol.ADMIN
                && Boolean.TRUE.equals(nuevoActivo);

        if (eraAdminActivo && !seguiranAdminActivo
                && usuarioRepository.countByRolAndActivoTrue(Usuario.Rol.ADMIN) <= 1) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Debe existir al menos un administrador activo");
        }
    }

    private String normalizarActor(String usernameActual) {
        return usernameActual == null || usernameActual.isBlank()
                ? usernameActual()
                : usernameActual;
    }

    private Map<String, Object> datosEditablesUsuario(Usuario usuario) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", usuario.getNombre());
        datos.put("email", usuario.getEmail());
        datos.put("rol", usuario.getRol() == null ? null : usuario.getRol().name());
        datos.put("activo", usuario.getActivo());
        return datos;
    }

    private void validarFormatoUsernameNuevo(String username) {
        if (username.length() < 3 || username.length() > 50) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    UsuarioUnicidad.MSG_USERNAME_LARGO);
        }
        if (!UsuarioUnicidad.PATRON_USERNAME.matcher(username).matches()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    UsuarioUnicidad.MSG_USERNAME_FORMATO);
        }
    }

    private void validarEmailDisponible(String email, Long excluirId) {
        if (email == null) {
            return;
        }
        boolean existe = excluirId == null
                ? usuarioRepository.existsByEmailIgnoreCase(email)
                : usuarioRepository.existsByEmailIgnoreCaseAndIdNot(email, excluirId);
        if (existe) {
            throw UsuarioUnicidad.emailDuplicado();
        }
    }

    private Usuario guardarUsuario(Usuario usuario) {
        try {
            return usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            NegocioException traducida = UsuarioUnicidad.desdeViolacion(ex);
            if (traducida != null) {
                throw traducida;
            }
            throw ex;
        }
    }

    private String normalizarUsername(String username) {
        return username == null ? null : username.trim();
    }

    private String normalizarEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }
}
