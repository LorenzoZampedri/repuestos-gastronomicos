package com.repuestos.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {
    
    @Value("${JWT_SECRET:default-secret-key}")
    private String secret;

    @Value("${app.vista-como.ttl-minutos:30}")
    private long vistaTtlMinutos;
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    
    public String generateToken(UserDetails userDetails, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return createToken(claims, userDetails.getUsername(), 1000 * 60 * 60 * 24); // 24 hours
    }

    public String generateVistaToken(UserDetails userDetails, String role, String adminUsername) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("imp", adminUsername);
        claims.put("ro", true);
        return createToken(claims, userDetails.getUsername(), vistaTtlMinutos * 60 * 1000);
    }

    public String extractImpersonator(String token) {
        return extractClaim(token, claims -> claims.get("imp", String.class));
    }

    public boolean extractReadOnly(String token) {
        return Boolean.TRUE.equals(extractClaim(token, claims -> claims.get("ro", Boolean.class)));
    }

    public Instant extractExpirationInstant(String token) {
        return extractExpiration(token).toInstant();
    }

    public long getVistaTtlMinutos() {
        return vistaTtlMinutos;
    }

    private String createToken(Map<String, Object> claims, String subject, long vigenciaMs) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + vigenciaMs))
                .signWith(getSigningKey())
                .compact();
    }
}
