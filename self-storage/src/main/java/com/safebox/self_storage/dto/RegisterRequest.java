package com.safebox.self_storage.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank @Size(max = 100) String fullName,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 20) String phone,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank String confirmPassword) {}
