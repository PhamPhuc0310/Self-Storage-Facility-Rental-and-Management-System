package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FacilityDetailResponse(
    UUID facilityId,
    String name,
    String address,
    String imagePath,
    String demoIntro,
    String demoSafety,
    String demoAccess,
    String demoTerms,
    Double latitude,
    Double longitude,
    String phone,
    String openingTime,
    String closingTime,
    Long totalAvailableUnits,
    List<FacilityUnitTypeDetailDto> unitTypes
) {}
