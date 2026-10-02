package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;

public record FacilityUnitTypeDetailDto(
    Integer typeId,
    String typeName,
    String storageMode,
    String sizeName,
    BigDecimal width,
    BigDecimal length,
    BigDecimal height,
    BigDecimal area,
    String features,
    BigDecimal monthlyPrice,
    BigDecimal depositAmount,
    Long availableUnits
) {}
