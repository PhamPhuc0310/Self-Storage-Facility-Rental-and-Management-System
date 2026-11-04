package com.safebox.self_storage.dto.manager;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record MeasurementSubmissionRequest(
    @NotNull @DecimalMin("0.001") @Digits(integer=5,fraction=3) BigDecimal lengthM,
    @NotNull @DecimalMin("0.001") @Digits(integer=5,fraction=3) BigDecimal widthM,
    @NotNull @DecimalMin("0.001") @Digits(integer=5,fraction=3) BigDecimal heightM,
    @NotBlank @Size(max=1000) String conditionNote,
    @NotBlank String source) {}
