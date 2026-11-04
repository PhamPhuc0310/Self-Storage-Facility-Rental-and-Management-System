package com.safebox.self_storage.dto.manager;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record StorageUnitRequest(@NotBlank String unitNumber, @NotNull Integer typeId, @NotBlank String floor,
        @NotBlank String zone, @NotBlank String status,
        @NotNull @DecimalMin("0.001") @Digits(integer=5, fraction=3) BigDecimal lengthM,
        @NotNull @DecimalMin("0.001") @Digits(integer=5, fraction=3) BigDecimal widthM,
        @NotNull @DecimalMin("0.001") @Digits(integer=5, fraction=3) BigDecimal heightM,
        boolean dimensionsVerified, boolean readinessVerified) {}
