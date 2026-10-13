package com.safebox.self_storage.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record RefundRequest(@NotNull @DecimalMin("1") BigDecimal amount,
                            @NotBlank @Size(max = 500) String reason) {}
