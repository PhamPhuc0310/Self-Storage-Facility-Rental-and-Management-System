package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FacilityListResponse(
    UUID facilityId,
    String name,
    String address,
    Long availableUnitsCount,
    BigDecimal startingPrice,
    List<FacilityUnitTypeDto> unitTypes
) {}
