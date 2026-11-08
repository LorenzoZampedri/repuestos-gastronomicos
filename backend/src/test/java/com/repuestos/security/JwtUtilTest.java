package com.repuestos.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private UserDetails observado;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "clave-de-test-suficientemente-larga-para-hs256-123456");
        ReflectionTestUtils.setField(jwtUtil, "vistaTtlMinutos", 30L);
        observado = User.withUsername("maria")
                .password("x")
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLEADO"))
                .build();
    }

    @Test
    void elTokenDeVistaLlevaRolImpersonadorYMarcadeSoloLectura() {
        String token = jwtUtil.generateVistaToken(observado, "EMPLEADO", "admin");

        assertEquals("maria", jwtUtil.extractUsername(token));
        assertEquals("EMPLEADO", jwtUtil.extractClaim(token, c -> c.get("role", String.class)));
        assertEquals("admin", jwtUtil.extractImpersonator(token));
        assertTrue(jwtUtil.extractReadOnly(token));

        long minutos = Duration.between(Instant.now(),
                jwtUtil.extractExpirationInstant(token)).toMinutes();
        assertTrue(minutos >= 29 && minutos <= 30, "TTL esperado de 30 minutos, fue " + minutos);
    }

    @Test
    void elTokenNormalNoTieneClaimsDeVista() {
        String token = jwtUtil.generateToken(observado, "EMPLEADO");

        assertEquals("maria", jwtUtil.extractUsername(token));
        assertNull(jwtUtil.extractImpersonator(token));
        assertTrue(!jwtUtil.extractReadOnly(token));

        long horas = Duration.between(Instant.now(),
                jwtUtil.extractExpirationInstant(token)).toHours();
        assertTrue(horas >= 23 && horas <= 24, "TTL normal esperado de 24 horas, fue " + horas);
    }
}
