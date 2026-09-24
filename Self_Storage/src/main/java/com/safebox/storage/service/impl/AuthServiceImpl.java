package com.safebox.storage.service.impl;

import com.safebox.storage.dto.request.LoginRequest;
import com.safebox.storage.dto.request.RegisterRequest;
import com.safebox.storage.dto.response.AuthenticatedUserResponse;
import com.safebox.storage.dto.response.LoginResponse;
import com.safebox.storage.dto.response.LogoutResponse;
import com.safebox.storage.dto.response.RegisterResponse;
import com.safebox.storage.entity.Role;
import com.safebox.storage.entity.User;
import com.safebox.storage.exception.DuplicateEmailException;
import com.safebox.storage.exception.InactiveUserException;
import com.safebox.storage.exception.InvalidCredentialsException;
import com.safebox.storage.exception.RegistrationUnavailableException;
import com.safebox.storage.exception.RegistrationValidationException;
import com.safebox.storage.repository.RoleRepository;
import com.safebox.storage.repository.UserRepository;
import com.safebox.storage.security.JwtService;
import com.safebox.storage.security.SafeBoxUserPrincipal;
import com.safebox.storage.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String fullName = request.fullName().trim();
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phone().trim();

        if (fullName.length() < 2) {
            throw new RegistrationValidationException("Full name must be at least 2 characters");
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new RegistrationValidationException("Password and confirmation do not match.");
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        Role customerRole = roleRepository.findByNameIgnoreCase("CUSTOMER")
                .orElseThrow(RegistrationUnavailableException::new);
        User user = User.createRegisteredCustomer(
                customerRole,
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                fullName,
                phone
        );

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return new RegisterResponse(
                    savedUser.getId(),
                    savedUser.getFullName(),
                    savedUser.getEmail(),
                    savedUser.getRole().getName(),
                    "Account created successfully. Please sign in."
            );
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
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
