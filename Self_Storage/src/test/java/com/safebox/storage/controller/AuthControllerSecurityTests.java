package com.safebox.storage.controller;

import com.safebox.storage.config.SecurityConfig;
import com.safebox.storage.dto.response.AuthenticatedUserResponse;
import com.safebox.storage.dto.response.LoginResponse;
import com.safebox.storage.dto.response.RegisterResponse;
import com.safebox.storage.exception.GlobalExceptionHandler;
import com.safebox.storage.exception.InactiveUserException;
import com.safebox.storage.exception.InvalidCredentialsException;
import com.safebox.storage.security.JwtAuthenticationFilter;
import com.safebox.storage.security.JwtService;
import com.safebox.storage.security.SafeBoxUserPrincipal;
import com.safebox.storage.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, HomeController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
class AuthControllerSecurityTests {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> UUID.randomUUID().toString() + UUID.randomUUID());
        registry.add("app.jwt.expiration-ms", () -> "60000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void loginIsPublicAndReturnsAuthenticationPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.login(any())).thenReturn(new LoginResponse(
                "signed.jwt.token",
                "Bearer",
                new AuthenticatedUserResponse(
                        userId,
                        "Customer One",
                        "customer1@safebox.vn",
                        "CUSTOMER"
                )
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer1@safebox.vn\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void loginValidationFailureReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationIsPublicAndIgnoresRoleEscalationFields() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.register(any())).thenReturn(new RegisterResponse(
                userId,
                "New Customer",
                "new.customer@safebox.vn",
                "CUSTOMER",
                "Account created successfully. Please sign in."
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"New Customer",
                                  "email":"new.customer@safebox.vn",
                                  "phone":"0901234567",
                                  "password":"SafeBox!2026",
                                  "confirmPassword":"SafeBox!2026",
                                  "role":"FACILITY_MANAGER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registrationValidatesRequiredNameAndEmailFormat() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"",
                                  "email":"not-an-email",
                                  "phone":"0901234567",
                                  "password":"SafeBox!2026",
                                  "confirmPassword":"SafeBox!2026"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCredentialsReturnUnauthorized() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@safebox.vn\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void inactiveAccountReturnsForbidden() throws Exception {
        when(authService.login(any())).thenThrow(new InactiveUserException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inactive@safebox.vn\",\"password\":\"123456\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedSessionEndpointRejectsMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/session")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedSessionEndpointAcceptsValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        SafeBoxUserPrincipal principal = new SafeBoxUserPrincipal(
                userId,
                "customer1@safebox.vn",
                "$2a$10$not-used-in-this-test",
                "CUSTOMER",
                true
        );
        when(userDetailsService.loadUserByUsername(principal.getUsername())).thenReturn(principal);
        when(authService.getAuthenticatedUser(principal.getUsername())).thenReturn(
                new AuthenticatedUserResponse(
                        userId,
                        "Customer One",
                        principal.getUsername(),
                        "CUSTOMER"
                )
        );
        String token = jwtService.generateToken(principal);

        mockMvc.perform(get("/api/auth/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.fullName").value("Customer One"))
                .andExpect(jsonPath("$.email").value("customer1@safebox.vn"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void staticDashboardAndRootEntryArePublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/safebox_storage_home/code.html"));

        mockMvc.perform(get("/safebox_storage_my_storage_dashboard/code.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        mockMvc.perform(get("/safebox_storage_home/code.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
