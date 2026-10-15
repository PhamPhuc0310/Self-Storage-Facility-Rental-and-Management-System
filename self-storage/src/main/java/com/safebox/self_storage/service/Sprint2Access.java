package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class Sprint2Access {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final RentalContractRepository contracts;
    private final StorageUnitRepository units;
    private final UserRepository users;
    private final UserFacilityAssignmentRepository assignments;

    public Sprint2Access(RentalContractRepository contracts, StorageUnitRepository units, UserRepository users, UserFacilityAssignmentRepository assignments) {
        this.contracts = contracts; this.units = units; this.users = users; this.assignments = assignments;
    }

    public UUID currentId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID id) return id;
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
    }
    public User currentUser() { return users.findById(currentId()).orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.")); }
    public String role() { return currentUser().getRole().getRoleName(); }
    public LocalDate today() { return LocalDate.now(ZONE); }
    public LocalDateTime now() { return LocalDateTime.now(ZONE); }
    public List<UUID> facilities() { return assignments.findActiveFacilityIds(currentId(), today()); }
    public UUID facilityId(RentalContract c) {
        return units.findById(c.unitId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kho của hợp đồng.")).getFacilityId();
    }
    public RentalContract contract(UUID id, boolean lock) {
        RentalContract r = (lock ? contracts.lockById(id) : contracts.findById(id))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng."));
        String role = role();
        boolean allowed = ("CUSTOMER".equals(role) && r.customerId.equals(currentId()))
                || (("FACILITY_STAFF".equals(role) || "FACILITY_MANAGER".equals(role)) && facilities().contains(facilityId(r)));
        if (!allowed) throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác với hợp đồng này.");
        return r;
    }
    public void confirmed(RentalContract r) {
        if (!"CONFIRMED".equals(r.status)) throw new BusinessException(HttpStatus.CONFLICT, "Hợp đồng chưa được xác nhận hoặc đã hủy.");
    }
}
