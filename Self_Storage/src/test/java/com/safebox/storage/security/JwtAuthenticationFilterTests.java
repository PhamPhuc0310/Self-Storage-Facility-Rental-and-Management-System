package com.safebox.storage.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenPopulatesSecurityContext() throws Exception {
        JwtService jwtService = new JwtService(
                "a-test-secret-that-is-at-least-thirty-two-bytes-long",
                60_000
        );
        SafeBoxUserPrincipal principal = new SafeBoxUserPrincipal(
                UUID.randomUUID(),
                "customer1@safebox.vn",
                "$2a$10$not-used-in-this-test",
                "CUSTOMER",
                true
        );
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername(principal.getUsername())).thenReturn(principal);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(principal));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(principal, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("ROLE_CUSTOMER", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
        verify(chain).doFilter(request, response);
    }
}
