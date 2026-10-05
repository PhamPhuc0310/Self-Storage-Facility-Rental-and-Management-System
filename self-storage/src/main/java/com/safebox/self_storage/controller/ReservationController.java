package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.CreateReservationRequest;
import com.safebox.self_storage.dto.response.ReservationResponse;
import com.safebox.self_storage.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller xử lý các yêu cầu API liên quan đến quy trình đặt kho (Reservation):
 * - UC05: Tạo yêu cầu đặt kho (Khách hàng)
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Hàm tiện ích: Trích xuất ID của người dùng hiện tại đang đăng nhập từ Security Context (JWT Token).
     *
     * @return UUID của người dùng hiện tại
     * @throws IllegalStateException nếu không tìm thấy thông tin đăng nhập hợp lệ
     */
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
        throw new IllegalStateException("Không thể xác định danh tính người dùng hiện tại");
    }

    // =========================================================================
    // UC05: Tạo yêu cầu đặt kho (Khách hàng - Customer)
    // =========================================================================

    /**
     * Chức năng UC05: Tạo mới một yêu cầu đặt kho tự quản.
     * 
     * Endpoint: POST /api/reservations
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param request Thông tin yêu cầu đặt kho gồm: cơ sở (facilityId), loại kho (typeId),
     *                ngày bắt đầu thuê (startDate), số tháng thuê (months) hoặc ngày kết thúc (endDate).
     * @return ResponseEntity chứa thông tin chi tiết yêu cầu đặt kho vừa tạo với mã HTTP 201 Created
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        UUID customerId = getCurrentUserId();
        ReservationResponse response = reservationService.createReservation(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
