package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.overdue.AdjustPenaltyRequest;
import com.safebox.self_storage.dto.overdue.OverdueContractResponse;
import com.safebox.self_storage.dto.overdue.ApprovePenaltyPaymentRequest;
import com.safebox.self_storage.dto.overdue.PayPenaltyRequest;
import com.safebox.self_storage.dto.overdue.PenaltyDetailResponse;
import com.safebox.self_storage.service.OverdueService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Controller xử lý các nghiệp vụ quá hạn và công nợ phí phạt dành cho Quản lý cơ sở (FACILITY_MANAGER) - UC18:
 * - BR18.1: Xác định hợp đồng quá hạn khi qua thời điểm kết thúc và chưa có gia hạn hợp lệ / đã hoàn tất trả kho.
 * - BR18.2: Công thức phí quá hạn tra cứu theo cấu hình rõ ràng.
 * - BR18.3: Mỗi kỳ phí phát sinh một lần (Idempotent), có log và giải thích cách tính.
 * - BR18.4: Quản lý chỉ xem/xử lý hợp đồng thuộc cơ sở phụ trách.
 * - BR18.5: Không tự động xóa nợ; nghiệp vụ điều chỉnh công nợ có lưu vết.
 */
@RestController
@RequestMapping("/api/manager/overdue")
public class OverdueManagerController {

    private final OverdueService overdueService;

    public OverdueManagerController(OverdueService overdueService) {
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
        throw new IllegalStateException("Không thể xác định danh tính quản lý từ phiên đăng nhập");
    }

    /**
     * UC18: Quản lý lấy danh sách các hợp đồng quá hạn tại cơ sở phụ trách (BR18.4).
     */
    @GetMapping("/contracts")
    public ResponseEntity<Page<OverdueContractResponse>> getOverdueContracts(
            @RequestParam(value = "facilityId", required = false) UUID facilityId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UUID managerId = getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)));
        Page<OverdueContractResponse> response = overdueService.getManagerOverdueContracts(
                managerId, facilityId, status, search, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * UC18: Xem chi tiết hợp đồng quá hạn và lịch sử phí phạt.
     */
    @GetMapping("/contracts/{contractId}")
    public ResponseEntity<OverdueContractResponse> getOverdueContractDetail(
            @PathVariable("contractId") UUID contractId) {
        UUID managerId = getCurrentUserId();
        OverdueContractResponse response = overdueService.getOverdueContractDetail(managerId, contractId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC18: Tính phí phạt quá hạn cho một hợp đồng cụ thể (Idempotent - BR18.3).
     */
    @PostMapping("/contracts/{contractId}/calculate-fee")
    public ResponseEntity<PenaltyDetailResponse> calculateOverdueFee(
            @PathVariable("contractId") UUID contractId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID managerId = getCurrentUserId();
        PenaltyDetailResponse response = overdueService.calculateOverdueFeeForContract(managerId, contractId, date);
        return ResponseEntity.ok(response);
    }

    /**
     * UC18: Quét và tính phí quá hạn hàng loạt cho toàn bộ cơ sở (Idempotent - BR18.3).
     */
    @PostMapping("/batch-calculate")
    public ResponseEntity<Map<String, Object>> batchCalculate(
            @RequestParam(value = "facilityId", required = false) UUID facilityId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID managerId = getCurrentUserId();
        int count = overdueService.batchCalculateOverdueFeesForFacility(managerId, facilityId, date);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", String.format("Đã quét và phát sinh phí quá hạn cho %d hợp đồng.", count),
                "generatedCount", count
        ));
    }

    /**
     * UC18: Quản lý điều chỉnh hoặc miễn giảm công nợ phí phạt có lưu vết kiểm toán (BR18.5).
     */
    @PostMapping("/penalties/{penaltyId}/adjust")
    public ResponseEntity<PenaltyDetailResponse> adjustPenalty(
            @PathVariable("penaltyId") UUID penaltyId,
            @Valid @RequestBody AdjustPenaltyRequest request) {
        UUID managerId = getCurrentUserId();
        PenaltyDetailResponse response = overdueService.adjustPenaltyDebt(managerId, penaltyId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * UC18: Thu tiền mặt trực tiếp tại quầy cho khoản phí phạt.
     */
    @PostMapping("/penalties/{penaltyId}/collect-cash")
    public ResponseEntity<PenaltyDetailResponse> collectCash(
            @PathVariable("penaltyId") UUID penaltyId,
            @Valid @RequestBody PayPenaltyRequest request) {
        UUID managerId = getCurrentUserId();
        PenaltyDetailResponse response = overdueService.payPenaltyDebt(managerId, penaltyId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * UC18: Quản lý cơ sở xác nhận / duyệt thanh toán khi khách hàng trả phí phạt trực tuyến.
     */
    @PostMapping("/penalties/{penaltyId}/approve-payment")
    public ResponseEntity<PenaltyDetailResponse> approvePayment(
            @PathVariable("penaltyId") UUID penaltyId,
            @RequestBody ApprovePenaltyPaymentRequest request) {
        UUID managerId = getCurrentUserId();
        PenaltyDetailResponse response = overdueService.approvePenaltyPayment(
                managerId, penaltyId, request.isApproved(), request.getNote());
        return ResponseEntity.ok(response);
    }
}

