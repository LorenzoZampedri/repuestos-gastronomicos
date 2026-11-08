package com.repuestos.services;

import com.repuestos.dto.VistaComoResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Usuario;
import com.repuestos.security.ImpersonacionInfo;
import com.repuestos.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VistaComoService {

    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;
    private final AuditoriaService auditoriaService;

    public VistaComoResponse iniciar(Long usuarioId) {
        Usuario admin = usuarioService.usuarioActual();
        Usuario observado = usuarioService.buscarPorId(usuarioId);

        if (observado.getId().equals(admin.getId())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "No podés ver el sistema como vos mismo");
        }
        if (observado.getRol() == Usuario.Rol.ADMIN) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "No podés ver el sistema como otro administrador");
        }
        if (!Boolean.TRUE.equals(observado.getActivo())) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "No podés ver el sistema como un usuario desactivado");
        }

        UserDetails observadoDetails = usuarioService.loadUserByUsername(observado.getUsername());
        String token = jwtUtil.generateVistaToken(
                observadoDetails, observado.getRol().name(), admin.getUsername());
        Instant expiraEn = jwtUtil.extractExpirationInstant(token);
        long ttlMinutos = jwtUtil.getVistaTtlMinutos();

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("usuarioObservadoId", observado.getId());
        detalle.put("usuarioObservadoUsername", observado.getUsername());
        detalle.put("usuarioObservadoRol", observado.getRol().name());
        detalle.put("ttlMinutos", ttlMinutos);
        auditoriaService.registrarComo(AccionAuditoria.VISTA_COMO_INICIADA, admin,
                "Usuario", observado.getId(),
                "Empezó a ver el sistema como '" + observado.getNombre() + "' ("
                        + etiquetaRol(observado.getRol()) + "), en modo solo lectura",
                detalle);

        return new VistaComoResponse(
                token,
                observado.getUsername(),
                observado.getNombre(),
                observado.getRol().name(),
                observado.getFotoUrl() == null ? "" : observado.getFotoUrl(),
                expiraEn,
                true);
    }

    public void finalizar() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ImpersonacionInfo info = authentication != null
                && authentication.getDetails() instanceof ImpersonacionInfo datos
                ? datos : null;
        if (info == null) {
            throw new NegocioException(HttpStatus.CONFLICT, "No estás en modo vista");
        }

        Usuario admin = usuarioService.buscarPorUsername(info.adminUsername());
        Usuario observado = usuarioService.buscarPorUsername(info.observadoUsername());
        long ttlMinutos = jwtUtil.getVistaTtlMinutos();

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("usuarioObservadoId", observado.getId());
        detalle.put("usuarioObservadoUsername", observado.getUsername());
        detalle.put("usuarioObservadoRol", observado.getRol().name());
        detalle.put("ttlMinutos", ttlMinutos);
        auditoriaService.registrarComo(AccionAuditoria.VISTA_COMO_FINALIZADA, admin,
                "Usuario", observado.getId(),
                "Terminó de ver el sistema como '" + observado.getNombre() + "' ("
                        + etiquetaRol(observado.getRol()) + ")",
                detalle);
    }

    private String etiquetaRol(Usuario.Rol rol) {
        return switch (rol) {
            case ADMIN -> "Administrador";
            case EMPLEADO -> "Empleado";
            case CLIENTE -> "Cliente";
        };
    }
}
