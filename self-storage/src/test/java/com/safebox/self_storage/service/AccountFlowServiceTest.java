package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.RegisterRequest;
import com.safebox.self_storage.repository.AuthTokenRepository;
import com.safebox.self_storage.repository.RoleRepository;
import com.safebox.self_storage.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountFlowServiceTest {
    @Test
    void mismatchedConfirmationDoesNotCreateAccount() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        AuthTokenRepository tokens = mock(AuthTokenRepository.class);
        AuthMailService mail = mock(AuthMailService.class);
        AccountFlowService flow = new AccountFlowService(users, roles, tokens, new BCryptPasswordEncoder(), mail);

        assertThrows(AuthFlowException.class, () -> flow.register(new RegisterRequest(
                "Khách demo", "khach@example.test", "0900000000", "password-123", "password-456")));
        verifyNoInteractions(users, roles, tokens, mail);
    }
}
