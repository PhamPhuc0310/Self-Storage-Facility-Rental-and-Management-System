package com.safebox.self_storage.dto;

import jakarta.validation.constraints.*;

public record EmailRequest(@NotBlank @Email String email) {}
