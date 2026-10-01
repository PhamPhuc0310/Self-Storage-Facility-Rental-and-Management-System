package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.LoginRequest;
import com.safebox.self_storage.entity.Role;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.repository.UserRepository;
import com.safebox.self_storage.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AuthService auth = new AuthService(users, encoder, jwt);
    private User user;

    @BeforeEach
    void setup() {
        Role role = new Role();
        role.setRoleName("CUSTOMER");
        user = new User();
        user.setUserId(UUID.randomUUID());
        user.setEmail("customer@safebox.vn");
        user.setFullName("Customer");
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setPasswordHash(encoder.encode("correct-password"));
    }

    @Test
    void successfulLoginUsesBcryptAndReturnsOnlyPublicFields() {
        when(users.findByEmailIgnoreCase("customer@safebox.vn")).thenReturn(Optional.of(user));
        when(jwt.generate(user)).thenReturn("signed-token");
        var result = auth.login(new LoginRequest(" customer@safebox.vn ", "correct-password"));
        assertEquals("signed-token", result.token());
        assertEquals("Bearer", result.tokenType());
        assertEquals(user.getUserId(), result.user().userId());
        assertEquals("Customer", result.user().fullName());
        assertEquals(user.getEmail(), result.user().email());
        assertEquals("CUSTOMER", result.user().role());
        assertFalse(result.toString().contains(user.getPasswordHash()));
    }

    @Test
    void unknownEmailWrongPasswordAndInactiveUserAreRejected() {
        assertThrows(BadCredentialsException.class, () -> auth.login(new LoginRequest("missing@safebox.vn", "x")));
        when(users.findByEmailIgnoreCase("customer@safebox.vn")).thenReturn(Optional.of(user));
        assertThrows(BadCredentialsException.class, () -> auth.login(new LoginRequest(user.getEmail(), "wrong")));
        user.setStatus("LOCKED");
        assertThrows(BadCredentialsException.class, () -> auth.login(new LoginRequest(user.getEmail(), "correct-password")));
        verifyNoInteractions(jwt);
    }
}
