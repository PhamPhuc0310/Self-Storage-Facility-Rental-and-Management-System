package com.safebox.self_storage.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record RefundReview(@NotBlank String decision, BigDecimal approvedAmount,
                           @Size(max = 500) String note) {}
