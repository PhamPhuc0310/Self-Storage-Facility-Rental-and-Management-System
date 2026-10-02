package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record FacilityUnitTypeDto(
    Integer typeId,
    String sizeName,
    String storageMode,
    BigDecimal area,
    BigDecimal monthlyPrice
) {}
