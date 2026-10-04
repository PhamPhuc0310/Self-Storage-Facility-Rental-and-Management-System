package com.safebox.self_storage.dto.manager;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record UnitTypeRequest(@NotBlank String typeName, @NotBlank String storageMode, @NotBlank String sizeName,
        @NotNull @DecimalMin("0.01") BigDecimal width, @NotNull @DecimalMin("0.01") BigDecimal length,
        @NotNull @DecimalMin("0.01") BigDecimal height, @NotNull @DecimalMin("0.00") BigDecimal monthlyPrice,
        @NotBlank String status) {}
