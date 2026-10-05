package com.safebox.self_storage.dto.manager;
import java.math.BigDecimal;
public record ManagedUnitTypeResponse(Integer typeId, String typeName, String storageMode, String sizeName,
        BigDecimal width, BigDecimal length, BigDecimal height, BigDecimal monthlyPrice, String status) {}
