package com.safebox.self_storage.dto;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(@NotBlank String token,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank String confirmPassword) {}
