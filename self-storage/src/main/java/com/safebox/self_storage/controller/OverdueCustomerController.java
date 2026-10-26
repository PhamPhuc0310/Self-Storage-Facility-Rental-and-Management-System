package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.overdue.OverdueContractResponse;
import com.safebox.self_storage.dto.overdue.PayPenaltyRequest;
import com.safebox.self_storage.dto.overdue.PenaltyDetailResponse;
import com.safebox.self_storage.service.OverdueService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller xử lý tra cứu và thanh toán nợ quá hạn dành cho Khách hàng (ROLE_CUSTOMER) - UC18:
 * - BR18.4: CUSTOMER chỉ xem của mình.
 * - BR18.3: Giải thích cách tính phí minh bạch và thanh toán idempotent.
 */
@RestController
@RequestMapping("/api/customer/overdue")
public class OverdueCustomerController {

    private final OverdueService overdueService;

    public OverdueCustomerController(OverdueService overdueService) {
        this.overdueService = overdueService;
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
        throw new IllegalStateException("Không thể xác định danh tính khách hàng từ phiên đăng nhập");
    }

    /**
     * UC18: Khách hàng xem danh sách các hợp đồng đang bị quá hạn của mình (BR18.4).
     */
    @GetMapping("/contracts")
    public ResponseEntity<Page<OverdueContractResponse>> getMyOverdueContracts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UUID customerId = getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)));
        Page<OverdueContractResponse> response = overdueService.getCustomerOverdueContracts(customerId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * UC18: Khách hàng xem chi tiết phí phạt và công nợ quá hạn của hợp đồng.
     */
    @GetMapping("/contracts/{contractId}")
    public ResponseEntity<OverdueContractResponse> getMyOverdueContractDetail(
            @PathVariable("contractId") UUID contractId) {
        UUID customerId = getCurrentUserId();
        OverdueContractResponse response = overdueService.getOverdueContractDetail(customerId, contractId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC18: Khách hàng thanh toán phí phạt quá hạn trực tuyến (Chuyển khoản / VNPAY) - BR18.3.
     */
    @PostMapping("/penalties/{penaltyId}/pay")
    public ResponseEntity<PenaltyDetailResponse> payPenalty(
            @PathVariable("penaltyId") UUID penaltyId,
            @Valid @RequestBody PayPenaltyRequest request) {
        UUID customerId = getCurrentUserId();
        PenaltyDetailResponse response = overdueService.payPenaltyDebt(customerId, penaltyId, request);
        return ResponseEntity.ok(response);
    }
}
