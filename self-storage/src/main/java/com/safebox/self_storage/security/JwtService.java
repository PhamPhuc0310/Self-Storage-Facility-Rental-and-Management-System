package com.safebox.self_storage.security;

import com.safebox.self_storage.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMillis;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration}") long expirationMillis) {
        this(secret, expirationMillis, Clock.systemUTC());
    }

    JwtService(String secret, long expirationMillis, Clock clock) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || expirationMillis <= 0) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 UTF-8 bytes and expiration must be positive");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
        this.clock = clock;
    }

    public String generate(User user) {
        Date now = Date.from(clock.instant());
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("role", user.getRole().getRoleName())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    public UUID userId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
