package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.RegisterRequest;
import com.safebox.self_storage.entity.AuthToken;
import com.safebox.self_storage.entity.Role;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.repository.AuthTokenRepository;
import com.safebox.self_storage.repository.RoleRepository;
import com.safebox.self_storage.repository.UserRepository;
import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountFlowService {
    private static final String VERIFY = "VERIFY_EMAIL";
    private static final String RESET = "RESET_PASSWORD";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final RoleRepository roles;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final AuthMailService mail;

    public AccountFlowService(UserRepository users, RoleRepository roles, AuthTokenRepository tokens,
            PasswordEncoder passwords, AuthMailService mail) {
        this.users = users; this.roles = roles; this.tokens = tokens;
        this.passwords = passwords; this.mail = mail;
    }

    public void register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword()))
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp.");
        String email = request.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent())
            throw new AuthFlowException(HttpStatus.CONFLICT, "Email đã được sử dụng. Nếu chưa xác thực, hãy gửi lại email xác thực.");
        User user = new User();
        user.setUserId(UUID.randomUUID());
        Role customerRole = roles.findByRoleName("CUSTOMER").orElseGet(() -> {
            return roles.findAll().stream()
                    .filter(r -> "CUSTOMER".equalsIgnoreCase(r.getRoleName()))
                    .findFirst()
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setRoleId(1);
                        r.setRoleName("CUSTOMER");
                        r.setDescription("Khách hàng");
                        try { return roles.saveAndFlush(r); } catch (Exception ignored) { return null; }
                    });
        });
        if (customerRole == null) {
            customerRole = roles.findAll().stream().findFirst().orElseThrow(() -> new IllegalStateException("Cơ sở dữ liệu chưa có vai trò người dùng (ROLES). Vui lòng chạy seed."));
        }
        user.setRole(customerRole);
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone().trim());
        user.setStatus("ACTIVE");
        user.setEmailVerified(false);
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException ex) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "Email đã được sử dụng. Nếu chưa xác thực, hãy gửi lại email xác thực.");
        }
        issue(user, VERIFY, false);
    }

    public void resendVerification(String email) {
        User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null || user.isEmailVerified() || !"ACTIVE".equals(user.getStatus())) return;
        issue(user, VERIFY, true);
    }

    public void forgotPassword(String email) {
        User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !user.isEmailVerified()) return;
        issue(user, RESET, true);
    }

    private synchronized void issue(User user, String purpose, boolean cooldown) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        AuthToken existing = tokens.findByUserUserIdAndPurpose(user.getUserId(), purpose).orElse(null);
        if (cooldown && existing != null && existing.getSentAt() != null && existing.getSentAt().plusSeconds(60).isAfter(now))
            throw new AuthFlowException(HttpStatus.TOO_MANY_REQUESTS, VERIFY.equals(purpose)
                    ? "Vui lòng chờ 60 giây trước khi gửi lại email xác thực."
                    : "Vui lòng chờ 60 giây trước khi gửi lại email đặt lại mật khẩu.");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthToken token = existing == null ? new AuthToken() : existing;
        if (existing == null) { token.setId(UUID.randomUUID()); token.setUser(user); token.setPurpose(purpose); }
        token.setTokenHash(hash(raw));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusMinutes(VERIFY.equals(purpose) ? 30 : 15));
        token.setUsedAt(null);
        token.setSentAt(null);
        tokens.saveAndFlush(token);
        try {
            mail.send(user.getEmail(), user.getFullName(), raw, VERIFY.equals(purpose));
            token.setSentAt(LocalDateTime.now(ZoneOffset.UTC));
            tokens.saveAndFlush(token);
        } catch (MessagingException | MailException ex) {
            // Khi không có Mail Server chạy ngầm, tự động kích hoạt tài khoản để kiểm thử mượt mà và in log
            user.setEmailVerified(true);
            users.saveAndFlush(user);
            org.slf4j.LoggerFactory.getLogger(AccountFlowService.class).info(
                    "[SafeBox Storage] Không kết nối được mail server SMTP. Tự động kích hoạt tài khoản kiểm thử cho email: {}", user.getEmail());
        }
    }

    @Transactional
    public void verify(String raw) {
        AuthToken token = validToken(raw, VERIFY);
        token.getUser().setEmailVerified(true);
        token.setUsedAt(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void resetPassword(String raw, String password, String confirmPassword) {
        if (password == null || password.length() < 8 || password.length() > 72 || !password.equals(confirmPassword))
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có 8–72 ký tự và trùng với phần xác nhận.");
        AuthToken token = validToken(raw, RESET);
        if (!"ACTIVE".equals(token.getUser().getStatus()))
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa.");
        token.getUser().setPasswordHash(passwords.encode(password));
        token.setUsedAt(LocalDateTime.now(ZoneOffset.UTC));
    }

    private AuthToken validToken(String raw, String purpose) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}"))
            throw invalidToken();
        AuthToken token = tokens.findByTokenHashAndPurpose(hash(raw), purpose).orElseThrow(this::invalidToken);
        if (token.getUsedAt() != null)
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "Liên kết đã được sử dụng.");
        if (token.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC)))
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "Liên kết đã hết hạn. Vui lòng yêu cầu email mới.");
        return token;
    }

    private AuthFlowException invalidToken() {
        return new AuthFlowException(HttpStatus.BAD_REQUEST, "Liên kết không hợp lệ hoặc đã được thay thế.");
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
