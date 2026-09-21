package com.safebox.storage.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTests {

    private static final String SECRET = "a-test-secret-that-is-at-least-thirty-two-bytes-long";

    @Test
    void generatedTokenValidatesForSameActivePrincipal() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        SafeBoxUserPrincipal principal = principal(true);

        String token = jwtService.generateToken(principal);

        assertTrue(jwtService.isTokenValid(token, principal));
    }

    @Test
    void tokenDoesNotValidateForInactivePrincipalOrTampering() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        SafeBoxUserPrincipal activePrincipal = principal(true);
        String token = jwtService.generateToken(activePrincipal);

        SafeBoxUserPrincipal inactivePrincipal = new SafeBoxUserPrincipal(
                activePrincipal.getUserId(),
                activePrincipal.getUsername(),
                activePrincipal.getPassword(),
                activePrincipal.getRole(),
                false
        );

        assertFalse(jwtService.isTokenValid(token, inactivePrincipal));
        assertFalse(jwtService.isTokenValid(token + "tampered", activePrincipal));
    }

    private SafeBoxUserPrincipal principal(boolean active) {
        return new SafeBoxUserPrincipal(
                UUID.randomUUID(),
                "customer1@safebox.vn",
                "$2a$10$not-used-in-this-test",
                "CUSTOMER",
                active
        );
    }
}
