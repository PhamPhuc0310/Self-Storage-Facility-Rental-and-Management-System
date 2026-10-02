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
    BigDecimal minTemperature,
    BigDecimal maxTemperature,
    String demoIntro,
    String demoGoods,
    String demoConditions,
    String imagePath,
    BigDecimal monthlyPrice,
    BigDecimal depositAmount,
    Long availableUnits
) {}
