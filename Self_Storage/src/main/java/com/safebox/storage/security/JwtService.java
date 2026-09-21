package com.safebox.storage.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT expiration must be greater than zero");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(SafeBoxUserPrincipal principal) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim("userId", principal.getUserId().toString())
                .claim("email", principal.getUsername())
                .claim("role", principal.getRole())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public boolean isTokenValid(String token, SafeBoxUserPrincipal principal) {
        try {
            Claims claims = parseClaims(token);
            return principal.isEnabled()
                    && principal.getUserId().toString().equals(claims.getSubject())
                    && principal.getUsername().equalsIgnoreCase(claims.get("email", String.class))
                    && principal.getRole().equals(claims.get("role", String.class))
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
