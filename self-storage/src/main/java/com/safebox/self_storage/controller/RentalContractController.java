package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.response.RentalContractDetailResponse;
import com.safebox.self_storage.dto.response.RentalContractListItemResponse;
import com.safebox.self_storage.service.RentalContractService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller xử lý các yêu cầu API của Use Case UC15:
 * "Xem và quản lý các kho đang thuê" (View and Manage Rented Storage Units)
 *
 * Phân quyền (BR15.1, BR15.9):
 * - Khách hàng (CUSTOMER) chỉ xem được danh sách và chi tiết hợp đồng thuê của chính mình (lấy ID từ JWT).
 * - Quản lý cơ sở (FACILITY_MANAGER) chỉ xem được danh sách và hợp đồng thuộc cơ sở được phân công.
 */
@RestController
public class RentalContractController {

    private final RentalContractService rentalContractService;

    public RentalContractController(RentalContractService rentalContractService) {
        this.rentalContractService = rentalContractService;
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
        throw new IllegalStateException("Không thể xác định danh tính người dùng hiện tại từ phiên đăng nhập");
    }

    // =========================================================================
    // CUSTOMER API: Xem danh sách và chi tiết kho đang thuê của tôi
    // =========================================================================

    /**
     * UC15: Lấy danh sách kho/hợp đồng đang thuê của khách hàng đang đăng nhập.
     * Hỗ trợ tìm kiếm, lọc theo trạng thái và phân trang (BR15.2, BR15.3, BR15.4).
     */
    @GetMapping({"/api/customer/rentals", "/api/rentals/my"})
    public ResponseEntity<Page<RentalContractListItemResponse>> getMyRentals(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UUID customerId = getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)));
        Page<RentalContractListItemResponse> result = rentalContractService.getCustomerRentals(
                customerId, status, search, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * UC15: Xem chi tiết một hợp đồng thuê kho của khách hàng đang đăng nhập (BR15.5, BR15.9).
     */
    @GetMapping({"/api/customer/rentals/{contractId}", "/api/rentals/my/{contractId}"})
    public ResponseEntity<RentalContractDetailResponse> getMyRentalDetail(
            @PathVariable("contractId") UUID contractId) {
        UUID customerId = getCurrentUserId();
        RentalContractDetailResponse result = rentalContractService.getCustomerRentalDetail(
                customerId, contractId);
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // FACILITY MANAGER API: Quản lý kho đang thuê tại cơ sở phụ trách
    // =========================================================================

    /**
     * UC15: Lấy danh sách hợp đồng thuê tại cơ sở cho Quản lý cơ sở (BR15.1).
     */
    @GetMapping("/api/facility/rentals")
    public ResponseEntity<Page<RentalContractListItemResponse>> getFacilityRentals(
            @RequestParam(value = "facilityId", required = false) UUID facilityId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UUID managerId = getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)));
        Page<RentalContractListItemResponse> result = rentalContractService.getFacilityRentals(
                managerId, facilityId, status, search, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * UC15: Xem chi tiết hợp đồng thuê tại cơ sở cho Quản lý cơ sở.
     */
    @GetMapping("/api/facility/rentals/{contractId}")
    public ResponseEntity<RentalContractDetailResponse> getFacilityRentalDetail(
            @PathVariable("contractId") UUID contractId) {
        UUID managerId = getCurrentUserId();
        RentalContractDetailResponse result = rentalContractService.getFacilityRentalDetail(
                managerId, contractId);
        return ResponseEntity.ok(result);
    }
}
