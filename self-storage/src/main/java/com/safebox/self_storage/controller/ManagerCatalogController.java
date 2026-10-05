package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.manager.*;
import com.safebox.self_storage.entity.Facility;
import com.safebox.self_storage.service.ManagerCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager/facilities")
public class ManagerCatalogController {
    private final ManagerCatalogService catalog;
    public ManagerCatalogController(ManagerCatalogService catalog) { this.catalog = catalog; }
    @GetMapping public List<Facility> myFacilities(@AuthenticationPrincipal UUID managerId) { return catalog.myFacilities(managerId); }
    @PutMapping("/{facilityId}") public Facility updateFacility(@AuthenticationPrincipal UUID managerId, @PathVariable UUID facilityId, @Valid @RequestBody FacilityUpdateRequest request) { return catalog.updateFacility(managerId, facilityId, request); }
    @GetMapping("/{facilityId}/unit-types") public List<ManagedUnitTypeResponse> types(@AuthenticationPrincipal UUID managerId, @PathVariable UUID facilityId) { return catalog.unitTypes(managerId, facilityId); }
    @PostMapping("/{facilityId}/unit-types") public ManagedUnitTypeResponse createType(@AuthenticationPrincipal UUID managerId, @PathVariable UUID facilityId, @Valid @RequestBody UnitTypeRequest request) { return catalog.createUnitType(managerId, facilityId, request); }
    @PutMapping("/{facilityId}/unit-types/{typeId}") public ManagedUnitTypeResponse updateType(@AuthenticationPrincipal UUID managerId, @PathVariable UUID facilityId, @PathVariable Integer typeId, @Valid @RequestBody UnitTypeRequest request) { return catalog.updateUnitType(managerId, facilityId, typeId, request); }
    @GetMapping("/{facilityId}/units") public List<ManagedStorageUnitResponse> units(@AuthenticationPrincipal UUID managerId, @PathVariable UUID facilityId) { return catalog.units(managerId, facilityId); }
    @PostMapping("/{facilityId}/units") public ManagedStorageUnitResponse createUnit(@AuthenticationPrincipal UUID managerId, @PathVariable UUID facilityId, @Valid @RequestBody StorageUnitRequest request) { return catalog.createUnit(managerId, facilityId, request); }
    @PutMapping("/{facilityId}/units/{unitId}") public ManagedStorageUnitResponse updateUnit(@AuthenticationPrincipal UUID managerId, @PathVariable UUID facilityId, @PathVariable UUID unitId, @Valid @RequestBody StorageUnitRequest request) { return catalog.updateUnit(managerId, facilityId, unitId, request); }
}
