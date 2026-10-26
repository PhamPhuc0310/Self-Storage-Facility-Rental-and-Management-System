package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.renewal.RenewalResponse;
import com.safebox.self_storage.dto.renewal.ReviewRenewalRequest;
import com.safebox.self_storage.service.RenewalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller xử lý các nghiệp vụ xét duyệt gia hạn hợp đồng dành cho Quản lý cơ sở (FACILITY_MANAGER) - UC17:
 * - BR17.3: Quản lý kiểm tra điều kiện khả dụng của kho/cơ sở và yêu cầu trả kho đang mở trước khi duyệt.
 * - Hệ thống tính phí, tạo khoản phải thu (hóa đơn RENEWAL).
 */
@RestController
@RequestMapping("/api/manager/renewals")
public class RenewalManagerController {

    private final RenewalService renewalService;

    public RenewalManagerController(RenewalService renewalService) {
        this.renewalService = renewalService;
    }

    private UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        if (principal instanceof String str) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException ignored) {}
        }
        throw new IllegalStateException("Không thể xác định danh tính quản lý từ phiên đăng nhập");
    }

    /**
     * UC17: Quản lý lấy danh sách yêu cầu gia hạn tại cơ sở phụ trách.
     */
    @GetMapping
    public ResponseEntity<Page<RenewalResponse>> getFacilityRenewals(
            @RequestParam(value = "facilityId", required = false) UUID facilityId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UUID managerId = getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)));
        Page<RenewalResponse> response = renewalService.getFacilityRenewals(managerId, facilityId, status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * UC17: Quản lý xem chi tiết một yêu cầu gia hạn.
     */
    @GetMapping("/{renewalId}")
    public ResponseEntity<RenewalResponse> getRenewalDetail(
            @PathVariable("renewalId") UUID renewalId) {
        UUID managerId = getCurrentUserId();
        RenewalResponse response = renewalService.getRenewalDetail(managerId, renewalId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC17: Quản lý xét duyệt hoặc từ chối yêu cầu gia hạn (BR17.3).
     */
    @PostMapping("/{renewalId}/review")
    public ResponseEntity<RenewalResponse> reviewRenewal(
            @PathVariable("renewalId") UUID renewalId,
            @Valid @RequestBody ReviewRenewalRequest request) {
        UUID managerId = getCurrentUserId();
        RenewalResponse response = renewalService.reviewRenewal(managerId, renewalId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * UC17: Quản lý phê duyệt nhanh yêu cầu gia hạn (BR17.3).
     */
    @PostMapping("/{renewalId}/approve")
    public ResponseEntity<RenewalResponse> approveRenewal(
            @PathVariable("renewalId") UUID renewalId,
            @RequestBody(required = false) Map<String, Object> body) {
        UUID managerId = getCurrentUserId();
        Integer customHours = null;
        if (body != null && body.containsKey("customPaymentHours")) {
            try {
                customHours = Integer.valueOf(body.get("customPaymentHours").toString());
            } catch (Exception ignored) {}
        }
        ReviewRenewalRequest request = new ReviewRenewalRequest("APPROVE", null, customHours);
        RenewalResponse response = renewalService.reviewRenewal(managerId, renewalId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * UC17: Quản lý từ chối yêu cầu gia hạn kèm lý do (BR17.3).
     */
    @PostMapping("/{renewalId}/reject")
    public ResponseEntity<RenewalResponse> rejectRenewal(
            @PathVariable("renewalId") UUID renewalId,
            @RequestBody Map<String, String> body) {
        UUID managerId = getCurrentUserId();
        String reason = (body != null) ? body.get("reason") : null;
        ReviewRenewalRequest request = new ReviewRenewalRequest("REJECT", reason);
        RenewalResponse response = renewalService.reviewRenewal(managerId, renewalId, request);
        return ResponseEntity.ok(response);
    }
}
