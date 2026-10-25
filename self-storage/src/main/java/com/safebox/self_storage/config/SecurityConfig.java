package com.safebox.self_storage.config;

import com.safebox.self_storage.security.JwtAuthenticationFilter;
import com.safebox.self_storage.security.JwtService;
import com.safebox.self_storage.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, AuthService auth)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/**", "/error", "/", "/index.html", "/role-home.html", "/staff-tasks.html", "/stitch/**", "/auth.js", "/favicon.ico", "/api/facilities", "/api/facilities/**", "/api/pricing/**", "/api/availability", "/uc04-facility-detail.html", "/uc10-customer-contract.html", "/safebox_storage_home/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/manager/**").hasRole("FACILITY_MANAGER")
                        .requestMatchers("/api/staff/tasks").hasAnyRole("FACILITY_STAFF", "FACILITY_MANAGER")
                        .requestMatchers("/api/staff/**").hasRole("FACILITY_STAFF")
                        .requestMatchers("/api/contracts/**").hasAnyRole("CUSTOMER", "FACILITY_STAFF", "FACILITY_MANAGER")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/reservations").hasRole("CUSTOMER")
                        .requestMatchers("/api/reservations/my", "/api/reservations/my/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/contracts/my", "/api/contracts/my/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/invoices/my", "/api/invoices/my/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/customer/rentals", "/api/customer/rentals/**", "/api/rentals/my", "/api/rentals/my/**").hasAnyRole("CUSTOMER", "SYSTEM_ADMIN", "FACILITY_MANAGER")
                        .requestMatchers("/api/facility/rentals", "/api/facility/rentals/**").hasAnyRole("FACILITY_STAFF", "FACILITY_MANAGER", "SYSTEM_ADMIN")
                        .requestMatchers("/api/facility/returns", "/api/facility/returns/**").hasAnyRole("FACILITY_STAFF", "FACILITY_MANAGER", "SYSTEM_ADMIN")
                        .requestMatchers("/api/facility/contracts/**", "/api/v1/return-inspections/**", "/api/v1/contracts/**").hasAnyRole("CUSTOMER", "FACILITY_STAFF", "FACILITY_MANAGER", "SYSTEM_ADMIN")
                        .requestMatchers("/api/support-tickets/**", "/api/v1/support-tickets/**").hasAnyRole("CUSTOMER", "FACILITY_STAFF", "FACILITY_MANAGER", "SYSTEM_ADMIN")
                        .requestMatchers("/api/role/customer").hasRole("CUSTOMER")
                        .requestMatchers("/api/role/staff").hasRole("FACILITY_STAFF")
                        .requestMatchers("/api/role/manager").hasRole("FACILITY_MANAGER")
                        .requestMatchers("/api/role/operations").hasRole("BUSINESS_OPERATIONS_MANAGER")
                        .requestMatchers("/api/role/admin").hasRole("SYSTEM_ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> writeError(response, 401, "Unauthorized"))
                        .accessDeniedHandler((request, response, ex) -> writeError(response, 403, "Forbidden")))
                .addFilterBefore(new JwtAuthenticationFilter(jwt, auth), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}