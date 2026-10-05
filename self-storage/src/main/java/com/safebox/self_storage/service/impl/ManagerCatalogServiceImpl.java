package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.manager.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.ManagerCatalogService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ManagerCatalogServiceImpl implements ManagerCatalogService {
    private static final Set<String> FACILITY_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> TYPE_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> UNIT_STATUSES = Set.of("AVAILABLE", "MAINTENANCE", "INACTIVE");
    private final FacilityRepository facilities; private final StorageUnitTypeRepository types;
    private final StorageUnitRepository units; private final PricingPolicyRepository pricing;
    private final UserFacilityAssignmentRepository assignments;
    public ManagerCatalogServiceImpl(FacilityRepository facilities, StorageUnitTypeRepository types,
            StorageUnitRepository units, PricingPolicyRepository pricing, UserFacilityAssignmentRepository assignments) {
        this.facilities = facilities; this.types = types; this.units = units; this.pricing = pricing; this.assignments = assignments;
    }
    public List<Facility> myFacilities(UUID managerId) {
        return assignments.findActiveFacilityIds(managerId, LocalDate.now()).stream()
                .map(id -> facilities.findById(id)).flatMap(Optional::stream).toList();
    }
    public Facility updateFacility(UUID managerId, UUID facilityId, FacilityUpdateRequest request) {
        Facility facility = requireManaged(managerId, facilityId);
        if (!FACILITY_STATUSES.contains(request.status())) invalid("Trạng thái cơ sở không hợp lệ.");
        facility.setName(request.name().trim()); facility.setAddress(request.address().trim()); facility.setStatus(request.status());
        return facilities.save(facility);
    }
    @Transactional(readOnly = true)
    public List<ManagedUnitTypeResponse> unitTypes(UUID managerId, UUID facilityId) {
        requireManaged(managerId, facilityId);
        List<PricingPolicy> policies = pricing.findByFacilityId(facilityId);
        Set<Integer> relevant = new HashSet<>();
        policies.forEach(p -> relevant.add(p.getTypeId()));
        types.findAll().forEach(t -> { if (units.existsByFacilityIdAndTypeId(facilityId, t.getTypeId())) relevant.add(t.getTypeId()); });
        return types.findAll().stream().filter(t -> relevant.contains(t.getTypeId()))
                .map(t -> typeResponse(facilityId, t, policies)).sorted(Comparator.comparing(ManagedUnitTypeResponse::typeId)).toList();
    }
    public ManagedUnitTypeResponse createUnitType(UUID managerId, UUID facilityId, UnitTypeRequest request) {
        requireManaged(managerId, facilityId); validateType(request);
        StorageUnitType type = new StorageUnitType(); apply(type, request); type = types.save(type);
        saveNewPrice(managerId, facilityId, type.getTypeId(), request.monthlyPrice());
        return typeResponse(facilityId, type, pricing.findByFacilityId(facilityId));
    }
    public ManagedUnitTypeResponse updateUnitType(UUID managerId, UUID facilityId, Integer typeId, UnitTypeRequest request) {
        requireManaged(managerId, facilityId); validateType(request);
        if (!units.existsByFacilityIdAndTypeId(facilityId, typeId)
                && pricing.findByFacilityId(facilityId).stream().noneMatch(p -> typeId.equals(p.getTypeId())))
            throw new BusinessException(HttpStatus.NOT_FOUND, "Loại kho không thuộc cơ sở này.");
        if (units.existsByTypeIdAndFacilityIdNot(typeId, facilityId)
                || pricing.existsByTypeIdAndFacilityIdNot(typeId, facilityId))
            throw new BusinessException(HttpStatus.CONFLICT, "Loại kho đang được dùng ở cơ sở khác và không thể chỉnh sửa tại đây.");
        StorageUnitType type = types.findById(typeId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy loại kho."));
        apply(type, request); types.save(type); replaceCurrentPrice(managerId, facilityId, typeId, request.monthlyPrice());
        return typeResponse(facilityId, type, pricing.findByFacilityId(facilityId));
    }
    @Transactional(readOnly = true)
    public List<ManagedStorageUnitResponse> units(UUID managerId, UUID facilityId) {
        requireManaged(managerId, facilityId);
        return units.findByFacilityIdOrderByUnitNumberAsc(facilityId).stream().map(this::unitResponse).toList();
    }
    public ManagedStorageUnitResponse createUnit(UUID managerId, UUID facilityId, StorageUnitRequest request) {
        requireManaged(managerId, facilityId); validateUnit(request);
        if (units.existsByFacilityIdAndUnitNumberIgnoreCase(facilityId, request.unitNumber().trim()))
            throw new BusinessException(HttpStatus.CONFLICT, "Mã kho vật lý đã tồn tại trong cơ sở.");
        assertFacilityType(facilityId, request.typeId());
        StorageUnit unit = new StorageUnit(); unit.setFacilityId(facilityId); apply(unit, request);
        return unitResponse(units.save(unit));
    }
    public ManagedStorageUnitResponse updateUnit(UUID managerId, UUID facilityId, UUID unitId, StorageUnitRequest request) {
        requireManaged(managerId, facilityId); validateUnit(request); assertFacilityType(facilityId, request.typeId());
        StorageUnit unit = units.findByUnitIdAndFacilityId(unitId, facilityId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kho vật lý."));
        if (!unit.getUnitNumber().equalsIgnoreCase(request.unitNumber().trim())
                && units.existsByFacilityIdAndUnitNumberIgnoreCase(facilityId, request.unitNumber().trim()))
            throw new BusinessException(HttpStatus.CONFLICT, "Mã kho vật lý đã tồn tại trong cơ sở.");
        apply(unit, request); return unitResponse(units.save(unit));
    }
    private Facility requireManaged(UUID managerId, UUID facilityId) {
        if (!assignments.findActiveFacilityIds(managerId, LocalDate.now()).contains(facilityId))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không được quản lý cơ sở này.");
        return facilities.findById(facilityId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở."));
    }
    private void assertFacilityType(UUID facilityId, Integer typeId) {
        StorageUnitType type = types.findById(typeId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy loại kho."));
        boolean configured = units.existsByFacilityIdAndTypeId(facilityId, typeId)
                || pricing.findByFacilityId(facilityId).stream().anyMatch(p -> typeId.equals(p.getTypeId()) && "ACTIVE".equals(p.getStatus()));
        if (!configured || !"ACTIVE".equals(type.getStatus())) invalid("Loại kho không hoạt động tại cơ sở này.");
    }
    private void validateType(UnitTypeRequest r) { if (!TYPE_STATUSES.contains(r.status())) invalid("Trạng thái loại kho không hợp lệ."); }
    private void validateUnit(StorageUnitRequest r) { if (!UNIT_STATUSES.contains(r.status())) invalid("Trạng thái kho vật lý không hợp lệ."); }
    private void invalid(String message) { throw new BusinessException(HttpStatus.BAD_REQUEST, message); }
    private void apply(StorageUnitType t, UnitTypeRequest r) { t.setTypeName(r.typeName().trim()); t.setStorageMode(r.storageMode().trim()); t.setSizeName(r.sizeName().trim()); t.setWidth(r.width()); t.setLength(r.length()); t.setHeight(r.height()); t.setStatus(r.status()); }
    private void apply(StorageUnit u, StorageUnitRequest r) { u.setUnitNumber(r.unitNumber().trim().toUpperCase()); u.setTypeId(r.typeId()); u.setFloor(r.floor().trim()); u.setZone(r.zone().trim()); u.setStatus(r.status()); }
    private ManagedStorageUnitResponse unitResponse(StorageUnit u) { return new ManagedStorageUnitResponse(u.getUnitId(),u.getUnitNumber(),u.getTypeId(),u.getFloor(),u.getZone(),u.getStatus()); }
    private ManagedUnitTypeResponse typeResponse(UUID facilityId, StorageUnitType t, List<PricingPolicy> policies) { return new ManagedUnitTypeResponse(t.getTypeId(),t.getTypeName(),t.getStorageMode(),t.getSizeName(),t.getWidth(),t.getLength(),t.getHeight(),CurrentPricing.forType(policies,t.getTypeId(),LocalDate.now()).map(PricingPolicy::getMonthlyPrice).orElse(null),t.getStatus()); }
    private void replaceCurrentPrice(UUID managerId, UUID facilityId, Integer typeId, BigDecimal value) { pricing.findByFacilityId(facilityId).stream().filter(p -> typeId.equals(p.getTypeId()) && "ACTIVE".equals(p.getStatus()) && p.getEffectiveTo()==null).forEach(p -> {p.setStatus("INACTIVE"); p.setEffectiveTo(LocalDate.now().minusDays(1));}); saveNewPrice(managerId, facilityId, typeId, value); }
    private void saveNewPrice(UUID managerId, UUID facilityId, Integer typeId, BigDecimal value) { PricingPolicy p = new PricingPolicy(); p.setFacilityId(facilityId); p.setTypeId(typeId); p.setCreatedBy(managerId); p.setMonthlyPrice(value); p.setDepositAmount(BigDecimal.ZERO); p.setDailyOverdueRate(BigDecimal.ZERO); p.setLatePaymentRate(BigDecimal.ZERO); p.setEarlyTerminationFee(BigDecimal.ZERO); p.setFeeWaiverAllowed(false); p.setEffectiveFrom(LocalDate.now()); p.setStatus("ACTIVE"); pricing.save(p); }
}
