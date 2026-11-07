package com.repuestos.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.LogActividad;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.LogActividadRepository;
import com.repuestos.repositories.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio central de auditoría. Todas las llamadas pasan por acá para garantizar:
 * whitelist por rol, redacción de datos sensibles, truncado de campos, resolución
 * segura de IP/user-agent y ausencia de excepciones que rompan el negocio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESUMEN = 500;
    private static final int MAX_DETALLE = 20000;
    private static final int MAX_USERNAME = 50;
    private static final int MAX_ROL = 20;
    private static final int MAX_ENTIDAD_TIPO = 30;
    private static final int MAX_IP = 45;
    private static final int MAX_USER_AGENT = 255;
    private static final int PROFUNDIDAD_MAXIMA = 10;
    private static final int SEGUNDOS_THROTTLE_ACCESO = 60;

    private final LogActividadRepository logActividadRepository;
    private final UsuarioRepository usuarioRepository;

    /** usuario + método + ruta -> instante del último registro de acceso denegado. */
    private final Map<String, Instant> ultimosAccesosDenegados = new ConcurrentHashMap<>();

    @Transactional
    public void registrar(AccionAuditoria accion, String entidadTipo, Long entidadId,
                          String resumen, Map<String, Object> detalle) {
        Actor actor = actorActual();
        if (actor == null) {
            return;
        }
        registrarInterno(accion, actor, entidadTipo, entidadId, resumen, detalle, "OK");
    }

    /** Variante con actor explícito, usada en el login cuando todavía no hay contexto. */
    @Transactional
    public void registrarComo(AccionAuditoria accion, Usuario actor, String entidadTipo,
                              Long entidadId, String resumen, Map<String, Object> detalle) {
        if (actor == null) {
            return;
        }
        Actor info = new Actor(actor.getId(), actor.getUsername(),
                actor.getRol() == null ? null : actor.getRol().name());
        registrarInterno(accion, info, entidadTipo, entidadId, resumen, detalle, "OK");
    }

    /**
     * LOGIN_FALLIDO es un evento de seguridad: se registra aunque no haya autenticación,
     * salvo que el usuario intentado exista y sea CLIENTE. Se guarda en una transacción
     * propia porque el login fallido no tiene una transacción de negocio que lo contenga.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarLoginFallido(String usernameIntentado, String motivo) {
        Usuario existente = buscarUsuarioParaLogin(usernameIntentado);
        if (existente != null && existente.getRol() == Usuario.Rol.CLIENTE) {
            return;
        }

        String username = truncar(
                usernameIntentado == null || usernameIntentado.isBlank()
                        ? "desconocido" : usernameIntentado.trim(),
                MAX_USERNAME);
        Actor actor = existente != null
                ? new Actor(existente.getId(), existente.getUsername(), existente.getRol().name())
                : new Actor(null, username, null);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("motivo", motivo);
        String resumen = "Inicio de sesión fallido para '" + username + "'";
        registrarInterno(AccionAuditoria.LOGIN_FALLIDO, actor, "Usuario", actor.usuarioId,
                resumen, detalle, "FALLIDO");
    }

    /**
     * ACCESO_DENEGADO se registra como máximo una vez por minuto para la misma
     * combinación usuario + método + ruta, para evitar inundar la tabla.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAccesoDenegado(String metodo, String ruta) {
        Actor actor = actorActual();
        if (actor == null) {
            return;
        }
        // Whitelist: un CLIENTE nunca genera registros de auditoría, ni siquiera accesos denegados.
        if ("CLIENTE".equals(actor.rol)) {
            return;
        }

        String clave = actor.username + "|" + metodo + "|" + ruta;
        Instant ahora = Instant.now();
        AtomicBoolean permitido = new AtomicBoolean(false);
        ultimosAccesosDenegados.compute(clave, (actual, ultimo) -> {
            if (ultimo != null && ultimo.plusSeconds(SEGUNDOS_THROTTLE_ACCESO).isAfter(ahora)) {
                return ultimo;
            }
            permitido.set(true);
            return ahora;
        });
        if (!permitido.get()) {
            return;
        }
        limpiarThrottleAntiguo(ahora);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("metodo", metodo);
        detalle.put("ruta", ruta);
        String resumen = "Intento de acceso denegado a " + metodo + " " + ruta;
        registrarInterno(AccionAuditoria.ACCESO_DENEGADO, actor, "Ruta", null,
                resumen, detalle, "FALLIDO");
    }

    /** Devuelve solo los campos que cambiaron como {campo: {antes, despues}}. */
    public Map<String, Object> calcularDiff(Map<String, Object> antes,
                                            Map<String, Object> despues) {
        Map<String, Object> diff = new LinkedHashMap<>();
        Set<String> campos = new LinkedHashSet<>();
        if (antes != null) {
            campos.addAll(antes.keySet());
        }
        if (despues != null) {
            campos.addAll(despues.keySet());
        }
        for (String campo : campos) {
            Object valorAntes = antes == null ? null : antes.get(campo);
            Object valorDespues = despues == null ? null : despues.get(campo);
            if (!mismoValor(valorAntes, valorDespues)) {
                Map<String, Object> cambio = new LinkedHashMap<>();
                cambio.put("antes", valorAntes);
                cambio.put("despues", valorDespues);
                diff.put(campo, cambio);
            }
        }
        return diff;
    }

    public String ipActual() {
        HttpServletRequest request = requestActual();
        if (request == null) {
            return null;
        }
        String remota = normalizarIp(request.getRemoteAddr());
        if (remota != null && esIpPrivada(remota)) {
            String real = normalizarIp(request.getHeader("X-Real-IP"));
            if (real != null) {
                return real;
            }
        }
        return remota;
    }

    public String userAgentActual() {
        HttpServletRequest request = requestActual();
        if (request == null) {
            return null;
        }
        return truncar(request.getHeader("User-Agent"), MAX_USER_AGENT);
    }

    /** Solo para tests: permite reiniciar el throttle en memoria. */
    public void limpiarThrottle() {
        ultimosAccesosDenegados.clear();
    }

    private void registrarInterno(AccionAuditoria accion, Actor actor, String entidadTipo,
                                  Long entidadId, String resumen, Map<String, Object> detalle,
                                  String resultado) {
        if (accion == null || actor == null) {
            return;
        }
        // Whitelist centralizada: ningún call-site puede registrar otra cosa de un CLIENTE.
        if ("CLIENTE".equals(actor.rol) && !accion.esDeCliente()) {
            return;
        }

        try {
            LogActividad registro = LogActividad.builder()
                    .fecha(LocalDateTime.now())
                    .usuarioId(actor.usuarioId)
                    .usuarioUsername(truncar(actor.username, MAX_USERNAME))
                    .usuarioRol(truncar(actor.rol, MAX_ROL))
                    .accion(accion.name())
                    .modulo(accion.getModulo())
                    .entidadTipo(truncar(entidadTipo, MAX_ENTIDAD_TIPO))
                    .entidadId(entidadId)
                    .resumen(truncar(resumen, MAX_RESUMEN))
                    .detalle(serializarDetalle(detalle))
                    .resultado(resultado)
                    .ip(truncar(ipActual(), MAX_IP))
                    .userAgent(truncar(userAgentActual(), MAX_USER_AGENT))
                    .build();
            logActividadRepository.save(registro);
        } catch (RuntimeException e) {
            // La auditoría nunca debe romper la operación de negocio.
            log.error("No se pudo registrar la auditoría de {}: {}", accion, e.getMessage());
        }
    }

    private Actor actorActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }
        String rol = authentication.getAuthorities() == null ? null
                : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
        Long usuarioId = usuarioRepository.findByUsernameIgnoreCase(username)
                .map(Usuario::getId)
                .orElse(null);
        return new Actor(usuarioId, username, rol);
    }

    private Usuario buscarUsuarioParaLogin(String username) {
        if (username == null || username.isBlank() || username.trim().length() > MAX_USERNAME) {
            return null;
        }
        return usuarioRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);
    }

    private HttpServletRequest requestActual() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private String serializarDetalle(Map<String, Object> detalle) {
        try {
            Object saneado = sanitizar(detalle, 0);
            String json = JSON.writeValueAsString(saneado == null ? Map.of() : saneado);
            if (json.length() > MAX_DETALLE) {
                return "{\"truncado\":true,\"motivo\":\"detalle demasiado largo\"}";
            }
            return json;
        } catch (Exception e) {
            return "{\"error\":\"detalle no serializable\"}";
        }
    }

    private Object sanitizar(Object valor, int profundidad) {
        if (profundidad > PROFUNDIDAD_MAXIMA) {
            return "[...]";
        }
        if (valor == null) {
            return null;
        }
        if (valor instanceof String texto) {
            return truncar(texto, MAX_DETALLE);
        }
        if (valor instanceof Number || valor instanceof Boolean) {
            return valor;
        }
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> resultado = new LinkedHashMap<>();
            int incluidos = 0;
            for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
                if (incluidos++ >= 200) {
                    resultado.put("_omitidos", "demasiadas claves");
                    break;
                }
                String clave = String.valueOf(entrada.getKey());
                Object sanitizado = esClaveSensible(clave)
                        ? "[REDACTADO]"
                        : sanitizar(entrada.getValue(), profundidad + 1);
                resultado.put(clave, sanitizado);
            }
            return resultado;
        }
        if (valor instanceof Iterable<?> iterable) {
            List<Object> resultado = new ArrayList<>();
            int incluidos = 0;
            for (Object item : iterable) {
                if (incluidos++ >= 500) {
                    resultado.add("[...]");
                    break;
                }
                resultado.add(sanitizar(item, profundidad + 1));
            }
            return resultado;
        }
        if (valor.getClass().isArray()) {
            int largo = java.lang.reflect.Array.getLength(valor);
            List<Object> resultado = new ArrayList<>();
            for (int i = 0; i < largo; i++) {
                resultado.add(sanitizar(java.lang.reflect.Array.get(valor, i), profundidad + 1));
            }
            return resultado;
        }
        try {
            return truncar(String.valueOf(valor), MAX_DETALLE);
        } catch (RuntimeException e) {
            return "[no serializable]";
        }
    }

    private boolean esClaveSensible(String clave) {
        String normalizada = clave == null ? "" : clave.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return normalizada.contains("password")
                || normalizada.contains("passwd")
                || normalizada.contains("contrasena")
                || normalizada.contains("token")
                || normalizada.contains("authorization")
                || normalizada.contains("secret")
                || normalizada.contains("apikey")
                || normalizada.contains("jwt")
                || normalizada.contains("credential")
                || normalizada.endsWith("hash");
    }

    private boolean mismoValor(Object antes, Object despues) {
        if (antes == null && despues == null) {
            return true;
        }
        if (antes instanceof BigDecimal a && despues instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return Objects.equals(antes, despues);
    }

    private String normalizarIp(String valor) {
        if (valor == null) {
            return null;
        }
        String ip = valor.trim();
        int coma = ip.indexOf(',');
        if (coma >= 0) {
            ip = ip.substring(0, coma).trim();
        }
        if (ip.isBlank() || ip.length() > MAX_IP) {
            return null;
        }
        if (!ip.matches("[0-9a-fA-F:.%\\-]+")) {
            return null;
        }
        return ip;
    }

    private boolean esIpPrivada(String ip) {
        if (ip == null) {
            return false;
        }
        if ("127.0.0.1".equals(ip) || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("169.254.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] partes = ip.split("\\.");
            if (partes.length >= 2) {
                try {
                    int segundo = Integer.parseInt(partes[1]);
                    if (segundo >= 16 && segundo <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // IP inválida: se trata como no privada.
                }
            }
        }
        String minuscula = ip.toLowerCase(Locale.ROOT);
        return minuscula.startsWith("fc") || minuscula.startsWith("fd")
                || minuscula.startsWith("fe80:");
    }

    private void limpiarThrottleAntiguo(Instant ahora) {
        if (ultimosAccesosDenegados.size() < 5000) {
            return;
        }
        ultimosAccesosDenegados.entrySet().removeIf(
                entrada -> entrada.getValue().plusSeconds(SEGUNDOS_THROTTLE_ACCESO * 2).isBefore(ahora));
    }

    private String truncar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }

    private record Actor(Long usuarioId, String username, String rol) {
    }
}
