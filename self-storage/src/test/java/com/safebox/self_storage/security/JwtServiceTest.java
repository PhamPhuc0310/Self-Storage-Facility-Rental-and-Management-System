package com.safebox.self_storage.security;

import com.safebox.self_storage.entity.Role;
import com.safebox.self_storage.entity.User;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "a-long-enough-test-secret-32-bytes-minimum";

    @Test
    void signedTokenAuthenticatesAndTamperingFails() {
        User user = user();
        JwtService service = new JwtService(SECRET, 3600000, Clock.systemUTC());
        String token = service.generate(user);
        assertEquals(user.getUserId(), service.userId(token));
        assertThrows(JwtException.class, () -> new JwtService("different-test-secret-over-32-characters", 3600000).userId(token));
        assertThrows(JwtException.class, () -> service.userId("not.a.jwt"));
    }

    @Test
    void expiredTokenFails() {
        String token = new JwtService(SECRET, 1000, Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)).generate(user());
        assertThrows(JwtException.class, () -> new JwtService(SECRET, 1000).userId(token));
    }

    private User user() {
        Role role = new Role();
        role.setRoleName("CUSTOMER");
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setRole(role);
        user.setPasswordHash("encoded-password");
        return user;
    }
}
