package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.CancelReservationRequest;
import com.safebox.self_storage.dto.CreateReservationRequest;
import com.safebox.self_storage.dto.RejectReservationRequest;
import com.safebox.self_storage.dto.response.ReservationResponse;
import com.safebox.self_storage.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các yêu cầu API liên quan đến quy trình đặt kho (Reservation):
 * - UC05: Tạo yêu cầu đặt kho (Khách hàng)
 * - UC06: Xem danh sách, xem chi tiết và hủy yêu cầu đặt kho của tôi (Khách hàng)
 * - UC08: Xem tất cả yêu cầu, duyệt hoặc từ chối yêu cầu đặt kho (Quản lý / Nhân viên)
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

    // =========================================================================
    // UC06: Xem và hủy yêu cầu của tôi (Khách hàng - Customer)
    // =========================================================================

    /**
     * Chức năng UC06: Lấy danh sách toàn bộ yêu cầu đặt kho của khách hàng đang đăng nhập.
     * 
     * Endpoint: GET /api/reservations/my
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param status (Tùy chọn) Lọc theo trạng thái yêu cầu (PENDING, CONFIRMED, REJECTED, CANCELLED hoặc ALL)
     * @param search (Tùy chọn) Từ khóa tìm kiếm theo mã yêu cầu (#SB-REQ-...), tên cơ sở, loại kho,...
     * @return Danh sách yêu cầu đặt kho của khách hàng hiện tại
     */
    @GetMapping("/my")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search) {
        UUID customerId = getCurrentUserId();
        return ResponseEntity.ok(reservationService.getMyReservations(customerId, status, search));
    }

    /**
     * Chức năng UC06: Xem thông tin chi tiết một yêu cầu đặt kho của khách hàng đang đăng nhập.
     * 
     * Endpoint: GET /api/reservations/my/{id}
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param id Mã định danh UUID của yêu cầu đặt kho
     * @return Chi tiết yêu cầu đặt kho thuộc về khách hàng hiện tại
     */
    @GetMapping("/my/{id}")
    public ResponseEntity<ReservationResponse> getMyReservationDetail(@PathVariable("id") UUID id) {
        UUID customerId = getCurrentUserId();
        return ResponseEntity.ok(reservationService.getMyReservationDetail(customerId, id));
    }

    /**
     * Chức năng UC06: Khách hàng tự hủy một yêu cầu đặt kho đang chờ xử lý của mình.
     * 
     * Endpoint: POST /api/reservations/my/{id}/cancel
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param id      Mã định danh UUID của yêu cầu cần hủy
     * @param request (Tùy chọn) Lý do hủy yêu cầu đặt kho từ khách hàng
     * @return Thông tin yêu cầu đặt kho sau khi đã cập nhật trạng thái CANCELLED
     */
    @PostMapping("/my/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancelMyReservation(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CancelReservationRequest request) {
        UUID customerId = getCurrentUserId();
        return ResponseEntity.ok(reservationService.cancelMyReservation(customerId, id, request));
    }

    /**
     * Chức năng UC06 (Dự phòng phương thức PUT): Khách hàng tự hủy một yêu cầu đặt kho của mình.
     *
     * Endpoint: PUT /api/reservations/my/{id}/cancel
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param id      Mã định danh UUID của yêu cầu cần hủy
     * @param request (Tùy chọn) Lý do hủy yêu cầu đặt kho từ khách hàng
     * @return Thông tin yêu cầu đặt kho sau khi đã cập nhật trạng thái CANCELLED
     */
    @PutMapping("/my/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancelMyReservationPut(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CancelReservationRequest request) {
        return cancelMyReservation(id, request);
    }

    // =========================================================================
    // UC08: Xem tất cả yêu cầu đặt kho (Quản lý / Nhân viên / Admin)
    // =========================================================================

    /**
     * Chức năng UC08: Xem danh sách tất cả các yêu cầu đặt kho trên hệ thống dành cho ban quản lý.
     * 
     * Endpoint: GET /api/reservations
     * Quyền truy cập: Nhân viên, Quản lý cơ sở, Quản lý vận hành, Quản trị hệ thống
     *
     * @param status     (Tùy chọn) Lọc theo trạng thái yêu cầu (PENDING, CONFIRMED, REJECTED, CANCELLED)
     * @param facilityId (Tùy chọn) Lọc theo mã cơ sở kho
     * @param search     (Tùy chọn) Tìm kiếm theo mã yêu cầu, tên khách hàng, số điện thoại,...
     * @return Danh sách yêu cầu đặt kho thỏa điều kiện tìm kiếm
     */
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getAllReservations(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "facilityId", required = false) UUID facilityId,
            @RequestParam(value = "search", required = false) String search) {
        return ResponseEntity.ok(reservationService.getAllReservations(status, facilityId, search));
    }

    /**
     * Chức năng UC08: Xem thông tin chi tiết một yêu cầu đặt kho bất kỳ theo mã ID.
     * 
     * Endpoint: GET /api/reservations/{id}
     * Quyền truy cập: Nhân viên, Quản lý cơ sở, Quản trị hệ thống
     *
     * @param id Mã định danh UUID của yêu cầu đặt kho
     * @return Chi tiết yêu cầu đặt kho tương ứng
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservationDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(reservationService.getReservationDetail(id));
    }

    /**
     * Chức năng UC08: Xác nhận (phê duyệt) yêu cầu đặt kho của khách hàng.
     * Kiểm tra trạng thái hiện tại phải là PENDING và cơ sở còn ô kho trống, chuyển trạng thái sang CONFIRMED.
     * 
     * Endpoint: POST /api/reservations/{id}/approve
     * Quyền truy cập: Nhân viên, Quản lý cơ sở, Quản trị hệ thống
     *
     * @param id Mã định danh UUID của yêu cầu cần duyệt
     * @return Chi tiết yêu cầu đặt kho với trạng thái mới là CONFIRMED (Đã xác nhận)
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ReservationResponse> approveReservation(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(reservationService.approveReservation(id));
    }

    /**
     * Chức năng UC08 (Alias confirm): Xác nhận yêu cầu đặt kho.
     * Tương tự phương thức approveReservation.
     * 
     * Endpoint: POST /api/reservations/{id}/confirm
     * Quyền truy cập: Nhân viên, Quản lý cơ sở, Quản trị hệ thống
     *
     * @param id Mã định danh UUID của yêu cầu cần duyệt
     * @return Chi tiết yêu cầu đặt kho với trạng thái mới là CONFIRMED
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ReservationResponse> confirmReservation(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(reservationService.approveReservation(id));
    }

    /**
     * Chức năng UC08: Từ chối yêu cầu đặt kho của khách hàng.
     * Kiểm tra trạng thái hiện tại phải là PENDING, ghi nhận lý do từ chối và chuyển trạng thái sang REJECTED.
     * 
     * Endpoint: POST /api/reservations/{id}/reject
     * Quyền truy cập: Nhân viên, Quản lý cơ sở, Quản trị hệ thống
     *
     * @param id      Mã định danh UUID của yêu cầu bị từ chối
     * @param request (Tùy chọn) Chứa lý do từ chối (rejectionReason) từ ban quản lý
     * @return Chi tiết yêu cầu đặt kho với trạng thái mới là REJECTED (Bị từ chối)
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ReservationResponse> rejectReservation(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) RejectReservationRequest request) {
        return ResponseEntity.ok(reservationService.rejectReservation(id, request));
    }
}
