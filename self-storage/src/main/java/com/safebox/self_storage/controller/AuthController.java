package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.LoginRequest;
import com.safebox.self_storage.dto.LoginResponse;
import com.safebox.self_storage.dto.UserView;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    public UserView me() {
        UUID id = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = auth.activeUser(id);
        return UserView.from(user);
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        return Map.of("message", "Logged out on client");
    }
}
