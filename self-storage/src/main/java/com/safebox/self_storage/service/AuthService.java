package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.LoginRequest;
import com.safebox.self_storage.dto.LoginResponse;
import com.safebox.self_storage.dto.UserView;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.repository.UserRepository;
import com.safebox.self_storage.security.JwtService;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!"ACTIVE".equals(user.getStatus()) || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!user.isEmailVerified())
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "Tài khoản chưa xác thực email. Hãy gửi lại email xác thực.");
        return new LoginResponse(jwt.generate(user), "Bearer", UserView.from(user));
    }

    public User activeUser(UUID id) {
        return users.findById(id).filter(user -> "ACTIVE".equals(user.getStatus()) && user.isEmailVerified()).orElse(null);
    }
}
