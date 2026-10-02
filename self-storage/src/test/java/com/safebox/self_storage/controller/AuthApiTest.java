package com.safebox.self_storage.controller;

import com.safebox.self_storage.config.SecurityConfig;
import com.safebox.self_storage.entity.Role;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.security.JwtService;
import com.safebox.self_storage.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, RoleProbeController.class})
@Import(SecurityConfig.class)
class AuthApiTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService auth;
    @MockBean JwtService jwt;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setup() {
        when(jwt.userId("valid")).thenReturn(id);
        Role role = new Role();
        role.setRoleName("CUSTOMER");
        User user = new User();
        user.setUserId(id);
        user.setRole(role);
        user.setFullName("Customer");
        user.setEmail("customer@safebox.vn");
        user.setStatus("ACTIVE");
        when(auth.activeUser(id)).thenReturn(user);
    }

    @Test
    void loginValidationRejectsMissingAndMalformedFields() throws Exception {
        for (String body : new String[]{"{}", "{\"email\":\"\",\"password\":\"x\"}",
                "{\"email\":\"a@b.vn\",\"password\":\"\"}",
                "{\"email\":\"bad\",\"password\":\"x\"}"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void missingInvalidAndValidTokensHaveCorrectStatus() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer bogus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(get("/api/role/customer").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/role/manager").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }
}

