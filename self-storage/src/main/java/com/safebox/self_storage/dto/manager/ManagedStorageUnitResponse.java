package com.safebox.self_storage.dto.manager;
import java.util.UUID;
public record ManagedStorageUnitResponse(UUID unitId, String unitNumber, Integer typeId, String floor, String zone, String status) {}
