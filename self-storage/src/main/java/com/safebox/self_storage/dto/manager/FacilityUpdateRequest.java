package com.safebox.self_storage.dto.manager;
import jakarta.validation.constraints.NotBlank;
public record FacilityUpdateRequest(@NotBlank String name, @NotBlank String address, @NotBlank String status) {}
