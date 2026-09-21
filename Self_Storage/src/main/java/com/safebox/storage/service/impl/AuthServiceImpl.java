package com.safebox.storage.service.impl;

import com.safebox.storage.dto.request.LoginRequest;
import com.safebox.storage.dto.response.AuthenticatedUserResponse;
import com.safebox.storage.dto.response.LoginResponse;
import com.safebox.storage.dto.response.LogoutResponse;
import com.safebox.storage.entity.User;
import com.safebox.storage.exception.InactiveUserException;
import com.safebox.storage.exception.InvalidCredentialsException;
import com.safebox.storage.repository.UserRepository;
import com.safebox.storage.security.JwtService;
import com.safebox.storage.security.SafeBoxUserPrincipal;
import com.safebox.storage.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new InactiveUserException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        SafeBoxUserPrincipal principal = SafeBoxUserPrincipal.from(user);
        String token = jwtService.generateToken(principal);
        AuthenticatedUserResponse authenticatedUser = new AuthenticatedUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getName()
        );
        return new LoginResponse(token, "Bearer", authenticatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getAuthenticatedUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new InactiveUserException();
        }

        return new AuthenticatedUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getName()
        );
    }

    @Override
    public LogoutResponse logout() {
        return new LogoutResponse("Logout successful");
    }
}
