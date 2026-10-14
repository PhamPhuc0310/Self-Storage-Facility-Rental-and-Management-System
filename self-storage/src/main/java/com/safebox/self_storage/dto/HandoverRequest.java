package com.safebox.self_storage.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record HandoverRequest(
        @NotBlank String conditionStatus,
        @NotBlank String accessMethod,
        @NotBlank String accessIdentifier,
        @NotBlank String customerSignature,
        String staffSignature,
        String note,
        BigDecimal temperature,
        BigDecimal humidity,
        @NotNull @DecimalMin("0.00") BigDecimal estimatedRepairCost) {}
