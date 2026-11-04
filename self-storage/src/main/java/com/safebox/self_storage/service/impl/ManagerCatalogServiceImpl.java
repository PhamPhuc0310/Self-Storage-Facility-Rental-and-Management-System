package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.manager.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.ManagerCatalogService;
import com.safebox.self_storage.service.Sprint2Access;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ManagerCatalogServiceImpl implements ManagerCatalogService {
    private static final Set<String> UNIT_STATUSES = Set.of("AVAILABLE", "MAINTENANCE", "INACTIVE");
    private final FacilityRepository facilities; private final StorageUnitTypeRepository types;
    private final StorageUnitRepository units; private final PricingPolicyRepository pricing;
    private final UserFacilityAssignmentRepository assignments;
    private final RentalContractRepository contracts;
    private final ActivityLogRepository activityLogs;
    public ManagerCatalogServiceImpl(FacilityRepository facilities, StorageUnitTypeRepository types,
            StorageUnitRepository units, PricingPolicyRepository pricing, UserFacilityAssignmentRepository assignments,
            RentalContractRepository contracts, ActivityLogRepository activityLogs) {
        this.facilities = facilities; this.types = types; this.units = units; this.pricing = pricing; this.assignments = assignments; this.contracts = contracts; this.activityLogs = activityLogs;
    }
    public List<Facility> myFacilities(UUID managerId) {
        return assignments.findActiveFacilityIds(managerId, LocalDate.now(Sprint2Access.ZONE)).stream()
                .map(id -> facilities.findById(id)).flatMap(Optional::stream).toList();
    }
    public Facility updateFacility(UUID managerId, UUID facilityId, FacilityUpdateRequest request) {
        throw new BusinessException(HttpStatus.FORBIDDEN, "Quản lý kinh doanh phụ trách thông tin cơ sở.");
    }
    @Transactional(readOnly = true)
    public List<ManagedUnitTypeResponse> unitTypes(UUID managerId, UUID facilityId) {
        requireManaged(managerId, facilityId);
        List<PricingPolicy> policies = pricing.findByFacilityId(facilityId);
        Set<Integer> relevant = new HashSet<>();
        policies.forEach(p -> relevant.add(p.getTypeId()));
        List<StorageUnitType> globalTypes = types.findAll();
        globalTypes.forEach(t -> { if (units.existsByFacilityIdAndTypeId(facilityId, t.getTypeId())) relevant.add(t.getTypeId()); });
        // Unit types are global; a new facility has no units or price yet.
        // Keep inactive types visible only when this facility already uses them.
        return globalTypes.stream().filter(t -> "ACTIVE".equals(t.getStatus()) || relevant.contains(t.getTypeId()))
                .map(t -> typeResponse(facilityId, t, policies)).sorted(Comparator.comparing(ManagedUnitTypeResponse::typeId)).toList();
    }
    public ManagedUnitTypeResponse createUnitType(UUID managerId, UUID facilityId, UnitTypeRequest request) {
        throw new BusinessException(HttpStatus.FORBIDDEN, "Danh mục giá thuộc quản lý kinh doanh.");
    }
    public ManagedUnitTypeResponse updateUnitType(UUID managerId, UUID facilityId, Integer typeId, UnitTypeRequest request) {
        throw new BusinessException(HttpStatus.FORBIDDEN, "Danh mục giá thuộc quản lý kinh doanh.");
    }
    @Transactional(readOnly = true)
    public List<ManagedStorageUnitResponse> units(UUID managerId, UUID facilityId) {
        requireManaged(managerId, facilityId);
        return units.findByFacilityIdOrderByUnitNumberAsc(facilityId).stream().map(this::unitResponse).toList();
    }
    public ManagedStorageUnitResponse createUnit(UUID managerId, UUID facilityId, StorageUnitRequest request) {
        Facility facility = requireManaged(managerId, facilityId); validateUnit(facilityId, request);
        if (request.dimensionsVerified() || "AVAILABLE".equals(request.status())) invalid("Tạo kho ở trạng thái chưa mở; xác minh số đo và mở cho thuê theo quy trình riêng.");
        if ("AVAILABLE".equals(request.status()) && !"ACTIVE".equals(facility.getStatus())) invalid("Cơ sở chưa hoạt động.");
        if (units.existsByFacilityIdAndUnitNumberIgnoreCase(facilityId, request.unitNumber().trim()))
            throw new BusinessException(HttpStatus.CONFLICT, "Mã kho vật lý đã tồn tại trong cơ sở.");
        assertFacilityType(facilityId, request.typeId());
        StorageUnit unit = new StorageUnit(); unit.setFacilityId(facilityId); apply(unit, request);
        return unitResponse(units.save(unit));
    }
    public ManagedStorageUnitResponse updateUnit(UUID managerId, UUID facilityId, UUID unitId, StorageUnitRequest request) {
        Facility facility = requireManaged(managerId, facilityId); validateUnit(facilityId, request); assertFacilityType(facilityId, request.typeId());
        if ("AVAILABLE".equals(request.status()) && !"ACTIVE".equals(facility.getStatus())) invalid("Cơ sở chưa hoạt động.");
        StorageUnit unit = units.findByUnitIdAndFacilityId(unitId, facilityId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kho vật lý."));
        if (request.dimensionsVerified() != unit.isDimensionsVerified())
            invalid("Xác minh kích thước qua quy trình số đo riêng.");
        if ("AVAILABLE".equals(request.status()) && !"AVAILABLE".equals(unit.getStatus()))
            invalid("Dùng thao tác Mở cho thuê sau khi hoàn tất kiểm tra.");
        if (unit.isDimensionsVerified() && (!sameDimension(unit.getLengthM(),request.lengthM())
                || !sameDimension(unit.getWidthM(),request.widthM()) || !sameDimension(unit.getHeightM(),request.heightM())))
            throw new BusinessException(HttpStatus.CONFLICT,"Không thể đổi số đo đã xác minh qua biểu mẫu chỉnh sửa.");
        if (!unit.getUnitNumber().equalsIgnoreCase(request.unitNumber().trim())
                && units.existsByFacilityIdAndUnitNumberIgnoreCase(facilityId, request.unitNumber().trim()))
            throw new BusinessException(HttpStatus.CONFLICT, "Mã kho vật lý đã tồn tại trong cơ sở.");
        if (contracts.existsByUnitId(unitId) && (!unit.getUnitNumber().equalsIgnoreCase(request.unitNumber().trim())
                || !unit.getTypeId().equals(request.typeId()) || !Objects.equals(unit.getLengthM(), request.lengthM())
                || !Objects.equals(unit.getWidthM(), request.widthM()) || !Objects.equals(unit.getHeightM(), request.heightM())
                || !unit.getFloor().equals(request.floor().trim()) || !unit.getZone().equals(request.zone().trim())))
            throw new BusinessException(HttpStatus.CONFLICT, "Kho đã có hợp đồng; không thể đổi định danh hoặc kích thước.");
        if (contracts.existsByUnitId(unitId) && "AVAILABLE".equals(request.status()))
            throw new BusinessException(HttpStatus.CONFLICT, "Kho có lịch sử hợp đồng; không thể tự mở lại trạng thái sẵn sàng.");
        apply(unit, request); return unitResponse(units.save(unit));
    }
    public ManagedStorageUnitResponse verifyDimensions(UUID managerId, UUID facilityId, UUID unitId, DimensionVerificationRequest request) {
        requireManaged(managerId, facilityId);
        if (request == null || !request.physicallyMeasured() || request.lengthM() == null || request.widthM() == null
                || request.heightM() == null || request.lengthM().signum() <= 0 || request.widthM().signum() <= 0
                || request.heightM().signum() <= 0 || request.lengthM().precision() - request.lengthM().scale() > 5
                || request.widthM().precision() - request.widthM().scale() > 5
                || request.heightM().precision() - request.heightM().scale() > 5
                || request.lengthM().scale() > 3 || request.widthM().scale() > 3 || request.heightM().scale() > 3)
            invalid("Cần đo và xác nhận đủ ba kích thước hợp lệ theo mét.");
        StorageUnit unit = units.lockById(unitId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kho vật lý."));
        if (!facilityId.equals(unit.getFacilityId()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Kho nằm ngoài cơ sở được phân công.");
        if (unit.isDimensionsVerified())
            throw new BusinessException(HttpStatus.CONFLICT, "Kích thước đã xác minh; không thể ghi đè qua quy trình bổ sung dữ liệu.");
        unit.setLengthM(request.lengthM()); unit.setWidthM(request.widthM()); unit.setHeightM(request.heightM());
        unit.setDimensionsVerified(true);
        unit.setMeasurementSource("PHYSICAL_INSPECTION");
        StorageUnit saved = units.save(unit);
        activityLogs.save(new ActivityLog(managerId, "VERIFY_UNIT_DIMENSIONS", "STORAGE_UNIT", unitId,
                "Đã đo trực tiếp dài=" + request.lengthM() + "m, rộng=" + request.widthM()
                        + "m, cao=" + request.heightM() + "m; thể tích=" + saved.getVolumeM3() + "m³",
                java.time.LocalDateTime.now(Sprint2Access.ZONE)));
        return unitResponse(saved);
    }
    private Facility requireManaged(UUID managerId, UUID facilityId) {
        // The current role is read from the database for every request, including old JWTs.
        var user = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (user == null || !user.getAuthorities().stream().anyMatch(a -> "ROLE_FACILITY_MANAGER".equals(a.getAuthority()))
                || !managerId.equals(user.getPrincipal()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản lý cơ sở được thao tác kho vật lý.");
        if (!assignments.findActiveFacilityIds(managerId, LocalDate.now(Sprint2Access.ZONE)).contains(facilityId))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không được quản lý cơ sở này.");
        return facilities.findById(facilityId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở."));
    }
    private void assertFacilityType(UUID facilityId, Integer typeId) {
        StorageUnitType type = types.findById(typeId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy loại kho."));
        if (!"ACTIVE".equals(type.getStatus())) invalid("Loại kho không hoạt động.");
    }
    private void validateUnit(UUID facilityId, StorageUnitRequest r) {
        if (!UNIT_STATUSES.contains(r.status())) invalid("Trạng thái kho vật lý không hợp lệ.");
        if (r.lengthM() == null || r.widthM() == null || r.heightM() == null || r.lengthM().signum() <= 0 || r.widthM().signum() <= 0 || r.heightM().signum() <= 0) invalid("Kích thước phải lớn hơn 0 m.");
        if ("AVAILABLE".equals(r.status()) && !r.dimensionsVerified()) invalid("Kích thước kho chưa được xác minh.");
        if ("AVAILABLE".equals(r.status()) && !r.readinessVerified()) invalid("Kho chưa được xác nhận sẵn sàng.");
        if ("AVAILABLE".equals(r.status()) && CurrentPricing.forType(pricing.findByFacilityId(facilityId), r.typeId(), LocalDate.now(Sprint2Access.ZONE))
                .filter(p -> "ACTIVE".equals(p.getStatus()) && p.getMonthlyPrice() != null && p.getMonthlyPrice().signum() > 0).isEmpty()) invalid("Kho chưa có giá thuê hiệu lực.");
    }
    private void invalid(String message) { throw new BusinessException(HttpStatus.BAD_REQUEST, message); }
    private boolean sameDimension(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }
    private void apply(StorageUnit u, StorageUnitRequest r) { u.setUnitNumber(r.unitNumber().trim().toUpperCase()); u.setTypeId(r.typeId()); u.setFloor(r.floor().trim()); u.setZone(r.zone().trim()); u.setStatus(r.status()); u.setLengthM(r.lengthM()); u.setWidthM(r.widthM()); u.setHeightM(r.heightM()); u.setDimensionsVerified(r.dimensionsVerified()); u.setReadinessVerified(r.readinessVerified()); }
    private ManagedStorageUnitResponse unitResponse(StorageUnit u) { return new ManagedStorageUnitResponse(u.getUnitId(),u.getUnitNumber(),u.getTypeId(),u.getFloor(),u.getZone(),u.getStatus(),u.getLengthM(),u.getWidthM(),u.getHeightM(),u.getVolumeM3(),u.isDimensionsVerified(),u.isReadinessVerified(),u.getMeasurementSource()); }
    private ManagedUnitTypeResponse typeResponse(UUID facilityId, StorageUnitType t, List<PricingPolicy> policies) { return new ManagedUnitTypeResponse(t.getTypeId(),t.getTypeName(),t.getStorageMode(),t.getSizeName(),t.getWidth(),t.getLength(),t.getHeight(),CurrentPricing.forType(policies,t.getTypeId(),LocalDate.now(Sprint2Access.ZONE)).map(PricingPolicy::getMonthlyPrice).orElse(null),t.getStatus()); }
}
