package com.safebox.self_storage.dto;

import jakarta.validation.constraints.*;

public record RefundCompletion(@NotBlank @Size(max = 100) String transactionReference) {}
