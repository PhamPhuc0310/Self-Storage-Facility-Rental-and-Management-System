package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.RegisterRequest;
import com.safebox.self_storage.repository.AuthTokenRepository;
import com.safebox.self_storage.repository.RoleRepository;
import com.safebox.self_storage.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.safebox.self_storage.entity.*;
import org.springframework.mail.MailSendException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccountFlowServiceTest {
    @Test
    void missingOrWrongCustomerRoleFailsBeforeSavingUserOrSendingMail() {
        var users=mock(UserRepository.class); var roles=mock(RoleRepository.class);
        var tokens=mock(AuthTokenRepository.class); var mail=mock(AuthMailService.class);
        var flow=new AccountFlowService(users,roles,tokens,new BCryptPasswordEncoder(),mail);
        Role privileged=new Role(); privileged.setRoleName("SYSTEM_ADMIN");
        when(roles.findByRoleName("CUSTOMER")).thenReturn(Optional.empty(), Optional.of(privileged));
        var request=new RegisterRequest("Khách", "role@example.test", "0900000000", "password-123", "password-123");
        for (int attempt=0; attempt<2; attempt++)
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                    assertThrows(AuthFlowException.class, () -> flow.register(request)).status());
        verify(users, never()).saveAndFlush(any());
        verify(roles, never()).saveAndFlush(any());
        verify(roles, never()).findAll();
        verifyNoInteractions(tokens, mail);
    }
    @Test
    void failedDeliveryStaysPendingAndCanRetryThenVerifyOnlyWithHashedToken() throws Exception {
        var users=mock(UserRepository.class);var roles=mock(RoleRepository.class);
        var tokens=mock(AuthTokenRepository.class);var mail=mock(AuthMailService.class);
        var flow=new AccountFlowService(users,roles,tokens,new BCryptPasswordEncoder(),mail);
        Role role=new Role();role.setRoleName("CUSTOMER");
        when(roles.findByRoleName("CUSTOMER")).thenReturn(Optional.of(role));
        doThrow(new MailSendException("offline")).doNothing().when(mail).send(anyString(),anyString(),anyString(),eq(true));
        var ex=assertThrows(AuthFlowException.class,()->flow.register(new RegisterRequest(
                "Khách hàng","pending@example.test","0900000000","password-123","password-123")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,ex.status());
        var user=ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(user.capture());
        assertFalse(user.getValue().isEmailVerified());
        var token=ArgumentCaptor.forClass(AuthToken.class);verify(tokens).saveAndFlush(token.capture());
        assertNull(token.getValue().getSentAt());assertNull(token.getValue().getUsedAt());
        when(users.findByEmailIgnoreCase("pending@example.test")).thenReturn(Optional.of(user.getValue()));
        when(tokens.findByUserUserIdAndPurpose(user.getValue().getUserId(),"VERIFY_EMAIL")).thenReturn(Optional.of(token.getValue()));
        flow.resendVerification("pending@example.test");
        var raw=ArgumentCaptor.forClass(String.class);verify(mail,times(2)).send(anyString(),anyString(),raw.capture(),eq(true));
        assertNotEquals(raw.getAllValues().get(0),raw.getValue());
        assertNotNull(token.getValue().getSentAt());assertFalse(user.getValue().isEmailVerified());
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getValue().getBytes(StandardCharsets.UTF_8)));
        assertEquals(hash,token.getValue().getTokenHash());assertNotEquals(raw.getValue(),hash);
        when(tokens.findByTokenHashAndPurpose(hash,"VERIFY_EMAIL")).thenReturn(Optional.of(token.getValue()));
        flow.verify(raw.getValue());assertTrue(user.getValue().isEmailVerified());assertNotNull(token.getValue().getUsedAt());
        assertThrows(AuthFlowException.class,()->flow.verify(raw.getValue()));
    }
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
