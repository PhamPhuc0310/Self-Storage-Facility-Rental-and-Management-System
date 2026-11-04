package com.safebox.self_storage.controller;

import com.safebox.self_storage.entity.EarlyReturnPolicy;
import com.safebox.self_storage.entity.RentalPolicy;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.EarlyReturnPolicyRepository;
import com.safebox.self_storage.repository.FacilityRepository;
import com.safebox.self_storage.repository.RentalPolicyRepository;
import com.safebox.self_storage.service.Sprint2Access;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Policy register; UC18 proposals are governed by OverduePolicyGovernanceController. */
@RestController
public class BusinessPolicyController {
    private final RentalPolicyRepository rentals;
    private final EarlyReturnPolicyRepository returns;
    private final FacilityRepository facilities;
    private final Sprint2Access access;
    public BusinessPolicyController(RentalPolicyRepository rentals, EarlyReturnPolicyRepository returns,
                                  FacilityRepository facilities, Sprint2Access access) {
        this.rentals=rentals; this.returns=returns; this.facilities=facilities; this.access=access;
    }
    public record PolicyRegister(List<RentalPolicy> rentalVersions, List<EarlyReturnPolicy> returnVersions,
                                 String depositRule, String vatRule, String approvalNote) {}
    public record CreateEarlyReturnPolicyRequest(
        String version,
        java.time.LocalDate effectiveFrom,
        java.time.LocalDate effectiveTo,
        Boolean depositRefundable,
        Boolean inspectionDeductionAllowed,
        Boolean approved
    ) {}

    @GetMapping("/api/business/policies")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public PolicyRegister business() {
        return register(rentals.findAll(),returns.findAll());
    }

    @PostMapping("/api/business/facilities/{facilityId}/early-return-policies")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public EarlyReturnPolicy createEarlyReturnPolicy(@PathVariable UUID facilityId,
                                                   @RequestBody CreateEarlyReturnPolicyRequest req) {
        if (req == null || req.approved == null || !req.approved) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Business approval is required before activating an early-return policy.");
        }
        if (req.version == null || req.version.trim().isEmpty() || req.effectiveFrom == null
                || req.depositRefundable == null || req.inspectionDeductionAllowed == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "All required policy values must be supplied.");
        }
        if (req.effectiveTo != null && req.effectiveTo.isBefore(req.effectiveFrom)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Policy effective date range is invalid.");
        }
        if (!facilities.existsById(facilityId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Facility does not exist.");
        }
        List<EarlyReturnPolicy> existing = returns.findAll().stream().filter(p -> facilityId.equals(p.facilityId)).toList();
        if (existing.stream().anyMatch(p -> req.version.trim().equalsIgnoreCase(p.version != null ? p.version.trim() : ""))) {
            throw new BusinessException(HttpStatus.CONFLICT, "Policy version already exists for this facility.");
        }
        java.time.LocalDate maxTo = req.effectiveTo != null ? req.effectiveTo : java.time.LocalDate.of(9999, 12, 31);
        boolean overlaps = existing.stream().filter(p -> "ACTIVE".equalsIgnoreCase(p.status)).anyMatch(p -> {
            java.time.LocalDate pFrom = p.effectiveFrom;
            java.time.LocalDate pTo = p.effectiveTo != null ? p.effectiveTo : java.time.LocalDate.of(9999, 12, 31);
            return !pFrom.isAfter(maxTo) && !pTo.isBefore(req.effectiveFrom);
        });
        if (overlaps) {
            throw new BusinessException(HttpStatus.CONFLICT, "Active policy dates overlap; close or review the existing version first.");
        }

        EarlyReturnPolicy policy = new EarlyReturnPolicy();
        policy.id = UUID.randomUUID();
        policy.facilityId = facilityId;
        policy.version = req.version.trim();
        policy.effectiveFrom = req.effectiveFrom;
        policy.effectiveTo = req.effectiveTo;
        policy.depositRefundable = req.depositRefundable;
        policy.inspectionDeductionAllowed = req.inspectionDeductionAllowed;
        policy.status = "ACTIVE";
        return returns.save(policy);
    }
    @GetMapping("/api/manager/facilities/{facilityId}/policies")
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    public PolicyRegister manager(@PathVariable UUID facilityId) {
        if (!access.facilities().contains(facilityId))
            throw new BusinessException(HttpStatus.FORBIDDEN,"Bạn không được xem chính sách cơ sở này.");
        return register(rentals.findAll().stream().filter(p -> p.getFacilityId()==null || facilityId.equals(p.getFacilityId())).toList(),
                returns.findAll().stream().filter(p -> facilityId.equals(p.facilityId)).toList());
    }
    private PolicyRegister register(List<RentalPolicy> rental, List<EarlyReturnPolicy> returns) {
        List<RentalPolicy> ordered=rental.stream().sorted(Comparator.comparing(RentalPolicy::getEffectiveFrom).reversed()).toList();
        List<EarlyReturnPolicy> returnOrdered=returns.stream().sorted(Comparator.comparing(p -> p.effectiveFrom, Comparator.reverseOrder())).toList();
        return new PolicyRegister(ordered,returnOrdered,
                "Tiền đặt cọc bằng một phần ba tổng giá trị hợp đồng đã gồm VAT.",
                "VAT 10% theo công thức hợp đồng hiện hành.",
                "Chính sách quá hạn chỉ được tính khi ACTIVE, đã được quản lý kinh doanh duyệt, có hiệu lực và không xung đột. Quy tắc đặt cọc, VAT, hoàn tiền và gia hạn vẫn chỉ đọc.");
    }
}
