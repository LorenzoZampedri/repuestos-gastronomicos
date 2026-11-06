package com.repuestos.services;

import com.repuestos.dto.UsuarioActualizarRequest;
import com.repuestos.dto.UsuarioCrearRequest;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = buscarPorUsername(username);

        return User.withUsername(usuario.getUsername())
                .password(usuario.getPasswordHash())
                .disabled(!Boolean.TRUE.equals(usuario.getActivo()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))
                .build();
    }

    public Usuario registrar(Usuario usuario) {
        if (usuario == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El usuario es obligatorio");
        }

        String username = normalizarUsername(usuario.getUsername());
        if (username == null || username.isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El nombre de usuario es obligatorio");
        }
        if (usuarioRepository.existsByUsernameIgnoreCase(username)) {
            throw new NegocioException(HttpStatus.CONFLICT, "El nombre de usuario ya existe");
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
        usuario.setActivo(usuario.getActivo() == null || usuario.getActivo());
        usuario.setPasswordHash(passwordEncoder.encode(usuario.getPasswordHash()));
        return usuarioRepository.save(usuario);
    }

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

        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(normalizarEmail(request.email()));
        usuario.setRol(request.rol());
        usuario.setActivo(request.activo());
        return usuarioRepository.save(usuario);
    }

    public Usuario cambiarActivo(Long id, Boolean activo, String usernameActual) {
        Usuario usuario = buscarPorId(id);
        String actor = normalizarActor(usernameActual);

        if (usuario.getUsername().equalsIgnoreCase(actor) && !Boolean.TRUE.equals(activo)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No podés desactivar tu propio usuario");
        }
        validarAdminActivo(usuario, usuario.getRol(), activo);

        usuario.setActivo(activo);
        return usuarioRepository.save(usuario);
    }

    public void resetPassword(Long id, String passwordNueva) {
        if (passwordNueva == null || passwordNueva.isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La contraseña nueva es obligatoria");
        }
        Usuario usuario = buscarPorId(id);
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(usuario);
    }

    public Usuario cambiarPasswordActual(String passwordActual, String passwordNueva) {
        Usuario usuario = verificarPasswordActual(passwordActual);
        if (passwordNueva == null || passwordNueva.isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La contraseña nueva es obligatoria");
        }
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        return usuarioRepository.save(usuario);
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

    private String normalizarUsername(String username) {
        return username == null ? null : username.trim();
    }

    private String normalizarEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }
}
