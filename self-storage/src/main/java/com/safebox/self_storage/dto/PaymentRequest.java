package com.safebox.self_storage.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PaymentRequest(@NotBlank String paymentType, @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
                             @NotBlank String paymentMethod, @NotBlank String idempotencyKey) {}
