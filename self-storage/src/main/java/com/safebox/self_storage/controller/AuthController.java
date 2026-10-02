package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.LoginRequest;
import com.safebox.self_storage.dto.LoginResponse;
import com.safebox.self_storage.dto.RegisterRequest;
import com.safebox.self_storage.dto.EmailRequest;
import com.safebox.self_storage.dto.ResetPasswordRequest;
import com.safebox.self_storage.dto.UserView;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.service.AuthService;
import com.safebox.self_storage.service.AccountFlowService;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AccountFlowService flow;
    private final boolean demoInbox;

    public AuthController(AuthService auth, AccountFlowService flow,
            @Value("${app.mail.demo-inbox:false}") boolean demoInbox) {
        this.auth = auth;
        this.flow = flow;
        this.demoInbox = demoInbox;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/register")
    public Map<String, String> register(@Valid @RequestBody RegisterRequest request) {
        flow.register(request);
        return Map.of("message", "Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.");
    }

    @PostMapping("/resend-verification")
    public Map<String, String> resend(@Valid @RequestBody EmailRequest request) {
        flow.resendVerification(request.email());
        return Map.of("message", "Nếu tài khoản chưa xác thực, email xác thực đã được gửi.");
    }

    @PostMapping("/verify")
    public Map<String, String> verify(@RequestParam String token) {
        flow.verify(token);
        return Map.of("message", "Xác thực tài khoản thành công. Vui lòng đăng nhập.");
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgot(@Valid @RequestBody EmailRequest request) {
        flow.forgotPassword(request.email());
        return Map.of("message", "Nếu email đã được đăng ký, bạn sẽ nhận được hướng dẫn đặt lại mật khẩu.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> reset(@Valid @RequestBody ResetPasswordRequest request) {
        flow.resetPassword(request.token(), request.password(), request.confirmPassword());
        return Map.of("message", "Đặt lại mật khẩu thành công. Vui lòng đăng nhập.");
    }

    @GetMapping("/demo-config")
    public Map<String, Boolean> demoConfig() {
        return Map.of("demoInbox", demoInbox);
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
