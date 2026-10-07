package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.manager.*;
import com.safebox.self_storage.entity.Facility;
import java.util.List;
import java.util.UUID;

public interface ManagerCatalogService {
    List<Facility> myFacilities(UUID managerId);
    Facility updateFacility(UUID managerId, UUID facilityId, FacilityUpdateRequest request);
    List<ManagedUnitTypeResponse> unitTypes(UUID managerId, UUID facilityId);
    ManagedUnitTypeResponse createUnitType(UUID managerId, UUID facilityId, UnitTypeRequest request);
    ManagedUnitTypeResponse updateUnitType(UUID managerId, UUID facilityId, Integer typeId, UnitTypeRequest request);
    List<ManagedStorageUnitResponse> units(UUID managerId, UUID facilityId);
    ManagedStorageUnitResponse createUnit(UUID managerId, UUID facilityId, StorageUnitRequest request);
    ManagedStorageUnitResponse updateUnit(UUID managerId, UUID facilityId, UUID unitId, StorageUnitRequest request);
}
