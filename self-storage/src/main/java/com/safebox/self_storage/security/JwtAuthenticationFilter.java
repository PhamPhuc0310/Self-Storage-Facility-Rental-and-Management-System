package com.safebox.self_storage.security;

import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.service.AuthService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AuthService auth;

    public JwtAuthenticationFilter(JwtService jwt, AuthService auth) {
        this.jwt = jwt;
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                UUID id = jwt.userId(token);
                User user = auth.activeUser(id);
                if (user != null && user.getRole() != null && jwt.isCurrent(token, user)) {
                    var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleName());
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(id, null, java.util.List.of(authority)));
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
