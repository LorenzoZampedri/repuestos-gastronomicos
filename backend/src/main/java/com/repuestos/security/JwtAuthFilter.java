package com.repuestos.security;

import com.repuestos.services.UsuarioService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String FIN_VISTA_COMO = "/api/auth/vista-como/fin";

    private final JwtUtil jwtUtil;
    private final UsuarioService usuarioService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final String username = jwtUtil.extractUsername(jwt);
            final String impersonator = jwtUtil.extractImpersonator(jwt);
            final boolean esVista = impersonator != null && !impersonator.isBlank();

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Regla 5: el admin que inició la vista debe seguir existiendo, activo y ser ADMIN.
                if (esVista && !adminValido(impersonator)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                UserDetails userDetails = usuarioService.loadUserByUsername(username);

                if (userDetails.isEnabled() && jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    if (esVista) {
                        authToken.setDetails(new ImpersonacionInfo(
                                impersonator, userDetails.getUsername(), rolPrincipal(userDetails)));
                    } else {
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    }
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Regla 2: una vista es de solo lectura, salvo finalizar la vista.
                    if (esVista && !esSoloLecturaPermitida(request)) {
                        escribirSoloLectura(response);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // Token inválido o expirado — continuar sin autenticación
        }

        filterChain.doFilter(request, response);
    }

    private boolean adminValido(String adminUsername) {
        try {
            UserDetails admin = usuarioService.loadUserByUsername(adminUsername);
            return admin.isEnabled() && admin.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_ADMIN"::equals);
        } catch (Exception e) {
            return false;
        }
    }

    private String rolPrincipal(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }

    private boolean esSoloLecturaPermitida(HttpServletRequest request) {
        String metodo = request.getMethod();
        if ("GET".equals(metodo) || "HEAD".equals(metodo) || "OPTIONS".equals(metodo)) {
            return true;
        }
        return "POST".equals(metodo) && FIN_VISTA_COMO.equals(request.getRequestURI());
    }

    private void escribirSoloLectura(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Estás en modo vista: solo podés mirar, no modificar.\","
                + "\"code\":\"MODO_VISTA_SOLO_LECTURA\"}");
    }
}
