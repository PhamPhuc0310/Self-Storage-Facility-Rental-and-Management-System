package com.safebox.storage.service.impl;

import com.safebox.storage.dto.request.LoginRequest;
import com.safebox.storage.dto.request.RegisterRequest;
import com.safebox.storage.entity.Role;
import com.safebox.storage.entity.User;
import com.safebox.storage.exception.InactiveUserException;
import com.safebox.storage.exception.DuplicateEmailException;
import com.safebox.storage.exception.InvalidCredentialsException;
import com.safebox.storage.exception.RegistrationValidationException;
import com.safebox.storage.repository.RoleRepository;
import com.safebox.storage.repository.UserRepository;
import com.safebox.storage.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTests {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private JwtService jwtService;
    private BCryptPasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        jwtService = mock(JwtService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthServiceImpl(userRepository, roleRepository, passwordEncoder, jwtService);
    }

    @Test
    void loginReturnsTokenAndDatabaseRoleForActiveUser() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "Customer One", "customer1@safebox.vn", "ACTIVE", "CUSTOMER", "123456");
        when(userRepository.findByEmailIgnoreCase("customer1@safebox.vn")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any())).thenReturn("signed.jwt.token");

        var response = authService.login(new LoginRequest(" Customer1@SafeBox.vn ", "123456"));

        assertEquals("signed.jwt.token", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(userId, response.user().userId());
        assertEquals("CUSTOMER", response.user().role());
    }

    @Test
    void loginRejectsUnknownEmailWithGenericCredentialsError() {
        when(userRepository.findByEmailIgnoreCase("missing@safebox.vn")).thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("missing@safebox.vn", "anything"))
        );

        assertEquals("Invalid email or password", exception.getMessage());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginRejectsWrongPasswordWithGenericCredentialsError() {
        User user = user(UUID.randomUUID(), "Customer One", "customer1@safebox.vn", "ACTIVE", "CUSTOMER", "123456");
        when(userRepository.findByEmailIgnoreCase("customer1@safebox.vn")).thenReturn(Optional.of(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("customer1@safebox.vn", "wrong-password"))
        );
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginRejectsInactiveUserBeforeIssuingToken() {
        User user = user(UUID.randomUUID(), "Inactive User", "inactive@safebox.vn", "INACTIVE", "CUSTOMER", "123456");
        when(userRepository.findByEmailIgnoreCase("inactive@safebox.vn")).thenReturn(Optional.of(user));

        assertThrows(
                InactiveUserException.class,
                () -> authService.login(new LoginRequest("inactive@safebox.vn", "123456"))
        );
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void authenticatedSessionReturnsCurrentDatabaseUser() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "Customer One", "customer1@safebox.vn", "ACTIVE", "CUSTOMER", "unused");
        when(userRepository.findByEmailIgnoreCase("customer1@safebox.vn")).thenReturn(Optional.of(user));

        var response = authService.getAuthenticatedUser("customer1@safebox.vn");

        assertEquals(userId, response.userId());
        assertEquals("Customer One", response.fullName());
        assertEquals("customer1@safebox.vn", response.email());
        assertEquals("CUSTOMER", response.role());
    }

    @Test
    void registrationCreatesActiveCustomerWithBcryptPassword() {
        Role customerRole = role("CUSTOMER");
        when(roleRepository.findByNameIgnoreCase("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(registerRequest("new.customer@safebox.vn"));

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertEquals("New Customer", saved.getFullName());
        assertEquals("new.customer@safebox.vn", saved.getEmail());
        assertEquals("0901234567", saved.getPhone());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals("CUSTOMER", saved.getRole().getName());
        assertTrue(passwordEncoder.matches("SafeBox!2026", saved.getPasswordHash()));
        assertNotEquals("SafeBox!2026", saved.getPasswordHash());
        assertEquals("CUSTOMER", response.role());
    }

    @Test
    void registrationRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("customer1@safebox.vn")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> authService.register(registerRequest("customer1@safebox.vn")));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registrationRejectsPasswordMismatch() {
        RegisterRequest request = new RegisterRequest(
                "New Customer", "new.customer@safebox.vn", "0901234567", "SafeBox!2026", "different-password"
        );

        assertThrows(RegistrationValidationException.class, () -> authService.register(request));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void newlyRegisteredCustomerCanLogin() {
        Role customerRole = role("CUSTOMER");
        when(roleRepository.findByNameIgnoreCase("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(registerRequest("new.customer@safebox.vn"));
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        when(userRepository.findByEmailIgnoreCase("new.customer@safebox.vn")).thenReturn(Optional.of(captor.getValue()));
        when(jwtService.generateToken(any())).thenReturn("new.customer.jwt");

        var login = authService.login(new LoginRequest("new.customer@safebox.vn", "SafeBox!2026"));

        assertEquals("new.customer.jwt", login.token());
        assertEquals("CUSTOMER", login.user().role());
        assertEquals("New Customer", login.user().fullName());
    }

    private User user(UUID id, String fullName, String email, String status, String roleName, String password) {
        Role role = role(roleName);

        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getFullName()).thenReturn(fullName);
        when(user.getEmail()).thenReturn(email);
        when(user.getStatus()).thenReturn(status);
        when(user.getRole()).thenReturn(role);
        when(user.getPasswordHash()).thenReturn(passwordEncoder.encode(password));
        return user;
    }

    private Role role(String roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        return role;
    }

    private RegisterRequest registerRequest(String email) {
        return new RegisterRequest(
                " New Customer ", email, "0901234567", "SafeBox!2026", "SafeBox!2026"
        );
    }
}
