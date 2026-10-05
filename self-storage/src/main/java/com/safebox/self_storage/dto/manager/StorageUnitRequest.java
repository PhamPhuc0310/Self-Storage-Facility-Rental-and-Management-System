package com.safebox.self_storage.dto.manager;
import jakarta.validation.constraints.*;
public record StorageUnitRequest(@NotBlank String unitNumber, @NotNull Integer typeId, @NotBlank String floor,
        @NotBlank String zone, @NotBlank String status) {}
