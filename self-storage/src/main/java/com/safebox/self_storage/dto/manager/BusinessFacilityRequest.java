package com.safebox.self_storage.dto.manager;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BusinessFacilityRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_-]{3,30}") String facilityCode,
        @NotBlank @Pattern(regexp = "[A-Z0-9_-]{2,30}") String regionCode,
        @NotBlank String name, @NotBlank String address,
        String phone, Double latitude, Double longitude, @NotBlank String status) {}
