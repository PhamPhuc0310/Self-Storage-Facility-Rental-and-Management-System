package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.manager.BusinessFacilityRequest;
import com.safebox.self_storage.entity.Facility;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.FacilityRepository;
import com.safebox.self_storage.repository.StorageUnitRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business/facilities")
@PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
public class BusinessFacilityController {
    private final FacilityRepository facilities;
    private final StorageUnitRepository units;
    private final com.safebox.self_storage.repository.UserFacilityAssignmentRepository assignments;
    public BusinessFacilityController(FacilityRepository facilities, StorageUnitRepository units,
            com.safebox.self_storage.repository.UserFacilityAssignmentRepository assignments) {
        this.facilities = facilities; this.units = units; this.assignments = assignments;
    }
    @GetMapping
    public List<Facility> list() { return facilities.findAll(); }
    @PostMapping
    @Transactional
    public Facility create(@Valid @RequestBody BusinessFacilityRequest request) {
        validate(request);
        if (facilities.existsByFacilityCodeIgnoreCase(request.facilityCode())
                || facilities.existsByNameIgnoreCaseAndAddressIgnoreCase(request.name().trim(), request.address().trim()))
            throw new BusinessException(HttpStatus.CONFLICT, "Cơ sở hoặc mã cơ sở đã tồn tại.");
        Facility facility = new Facility();
        apply(facility, request);
        try { return facilities.saveAndFlush(facility); }
        catch (DataIntegrityViolationException e) { throw new BusinessException(HttpStatus.CONFLICT, "Mã cơ sở đã tồn tại."); }
    }
    @PutMapping("/{id}")
    @Transactional
    public Facility update(@PathVariable UUID id, @Valid @RequestBody BusinessFacilityRequest request) {
        validate(request);
        Facility facility = facilities.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở."));
        if (facility.getFacilityCode() != null && !facility.getFacilityCode().equals(request.facilityCode()))
            throw new BusinessException(HttpStatus.CONFLICT, "Không thể đổi mã cơ sở đã lưu.");
        if (facility.getRegionCode() != null && !facility.getRegionCode().equals(request.regionCode())
                && (units.existsByFacilityId(id) || assignments.existsByFacilityId(id)))
            throw new BusinessException(HttpStatus.CONFLICT, "Không thể đổi vùng của cơ sở đã sử dụng.");
        if ("ACTIVE".equals(facility.getStatus()) && "INACTIVE".equals(request.status())
                && (units.existsByFacilityId(id) || assignments.existsByFacilityId(id)))
            throw new BusinessException(HttpStatus.CONFLICT, "Cơ sở còn kho hoặc nhân sự; cần xử lý vận hành trước khi ngừng hoạt động.");
        if (facilities.existsByFacilityCodeIgnoreCase(request.facilityCode()) && !request.facilityCode().equals(facility.getFacilityCode()))
            throw new BusinessException(HttpStatus.CONFLICT, "Mã cơ sở đã tồn tại.");
        if (facilities.findFirstByNameIgnoreCaseAndAddressIgnoreCase(request.name().trim(), request.address().trim())
                .filter(other -> !other.getFacilityId().equals(id)).isPresent())
            throw new BusinessException(HttpStatus.CONFLICT, "Cơ sở cùng tên và địa chỉ đã tồn tại.");
        apply(facility, request);
        return facilities.save(facility);
    }
    private void validate(BusinessFacilityRequest r) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(r.status()) || r.name().trim().length() < 3 || r.address().trim().length() < 8
                || (r.latitude() == null) != (r.longitude() == null)
                || (r.latitude() != null && (!Double.isFinite(r.latitude()) || !Double.isFinite(r.longitude())
                || r.latitude() < -90 || r.latitude() > 90 || r.longitude() < -180 || r.longitude() > 180)))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Thông tin cơ sở hoặc tọa độ không hợp lệ.");
    }
    private void apply(Facility f, BusinessFacilityRequest r) {
        f.setFacilityCode(r.facilityCode()); f.setRegionCode(r.regionCode());
        f.setName(r.name().trim()); f.setAddress(r.address().trim()); f.setStatus(r.status());
        f.setPhone(r.phone() == null ? null : r.phone().trim()); f.setLatitude(r.latitude()); f.setLongitude(r.longitude());
    }
}
