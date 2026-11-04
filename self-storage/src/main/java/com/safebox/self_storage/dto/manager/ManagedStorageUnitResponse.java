package com.safebox.self_storage.dto.manager;
import java.util.UUID;
import java.math.BigDecimal;
public record ManagedStorageUnitResponse(UUID unitId, String unitNumber, Integer typeId, String floor, String zone, String status,
        BigDecimal lengthM, BigDecimal widthM, BigDecimal heightM, BigDecimal volumeM3,
        boolean dimensionsVerified, boolean readinessVerified, String measurementSource) {}
