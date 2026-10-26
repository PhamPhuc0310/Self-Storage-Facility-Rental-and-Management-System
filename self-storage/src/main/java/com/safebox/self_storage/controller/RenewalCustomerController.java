package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.renewal.*;
import com.safebox.self_storage.service.RenewalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các yêu cầu gia hạn hợp đồng thuê dành cho Khách hàng (ROLE_CUSTOMER) - UC17:
 * - BR17.1: Khách hàng xem trước phí và gửi yêu cầu gia hạn cho hợp đồng ACTIVE của mình.
 * - BR17.2: Ngày bắt đầu kỳ gia hạn nối tiếp ngày kết thúc hiện tại; không ghi đè lịch sử kỳ cũ.
 * - BR17.4: Giá, VAT, ưu đãi được chốt thành snapshot của lần gia hạn.
 * - BR17.5: Thanh toán phí gia hạn để hợp đồng có hiệu lực kỳ hạn mới; retry không tạo gia hạn trùng.
 */
@RestController
public class RenewalCustomerController {

    private final RenewalService renewalService;

    public RenewalCustomerController(RenewalService renewalService) {
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
        throw new IllegalStateException("Không thể xác định danh tính khách hàng từ phiên đăng nhập");
    }

    /**
     * UC17: Tính toán xem trước báo giá cho kỳ gia hạn dự kiến (BR17.2, BR17.4).
     */
    @GetMapping({"/api/customer/contracts/{contractId}/renewals/preview", "/api/contracts/my/{contractId}/renewals/preview"})
    public ResponseEntity<RenewalPreviewResponse> previewRenewal(
            @PathVariable("contractId") UUID contractId,
            @RequestParam(value = "months", defaultValue = "1") Integer months) {
        UUID customerId = getCurrentUserId();
        RenewalPreviewResponse response = renewalService.previewRenewal(customerId, contractId, months);
        return ResponseEntity.ok(response);
    }

    /**
     * UC17: Khách hàng gửi yêu cầu gia hạn hợp đồng thuê kho (BR17.1, BR17.2, BR17.4).
     */
    @PostMapping({"/api/customer/contracts/{contractId}/renewals", "/api/contracts/my/{contractId}/renewals"})
    public ResponseEntity<RenewalResponse> requestRenewal(
            @PathVariable("contractId") UUID contractId,
            @Valid @RequestBody CreateRenewalRequest request) {
        UUID customerId = getCurrentUserId();
        RenewalResponse response = renewalService.requestRenewal(customerId, contractId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * UC17: Lấy lịch sử tất cả các lần gia hạn của một hợp đồng thuê (BR17.2).
     */
    @GetMapping({"/api/customer/contracts/{contractId}/renewals", "/api/contracts/my/{contractId}/renewals"})
    public ResponseEntity<List<RenewalResponse>> getContractRenewals(
            @PathVariable("contractId") UUID contractId) {
        UUID customerId = getCurrentUserId();
        List<RenewalResponse> response = renewalService.getContractRenewals(customerId, contractId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC17: Khách hàng xem danh sách các yêu cầu gia hạn của mình.
     */
    @GetMapping({"/api/customer/renewals/my", "/api/contracts/my/renewals"})
    public ResponseEntity<Page<RenewalResponse>> getMyRenewals(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UUID customerId = getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)));
        Page<RenewalResponse> response = renewalService.getCustomerRenewals(customerId, status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * UC17: Xem chi tiết một yêu cầu gia hạn.
     */
    @GetMapping({"/api/customer/renewals/{renewalId}", "/api/contracts/my/renewals/{renewalId}"})
    public ResponseEntity<RenewalResponse> getRenewalDetail(
            @PathVariable("renewalId") UUID renewalId) {
        UUID customerId = getCurrentUserId();
        RenewalResponse response = renewalService.getRenewalDetail(customerId, renewalId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC17: Khách hàng thanh toán phí gia hạn hợp đồng (BR17.5).
     * Chỉ gia hạn hiệu lực hợp đồng sau khi khoản thanh toán được xác nhận.
     */
    @PostMapping({"/api/customer/renewals/{renewalId}/pay", "/api/contracts/my/renewals/{renewalId}/pay"})
    public ResponseEntity<RenewalPaymentResponse> payRenewal(
            @PathVariable("renewalId") UUID renewalId,
            @Valid @RequestBody PayRenewalRequest request) {
        UUID customerId = getCurrentUserId();
        RenewalPaymentResponse response = renewalService.payRenewal(customerId, renewalId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * UC17: Khách hàng tự hủy yêu cầu gia hạn khi còn chờ duyệt hoặc chưa thanh toán.
     */
    @PostMapping({"/api/customer/renewals/{renewalId}/cancel", "/api/contracts/my/renewals/{renewalId}/cancel"})
    public ResponseEntity<RenewalResponse> cancelRenewal(
            @PathVariable("renewalId") UUID renewalId) {
        UUID customerId = getCurrentUserId();
        RenewalResponse response = renewalService.cancelRenewal(customerId, renewalId);
        return ResponseEntity.ok(response);
    }
}
