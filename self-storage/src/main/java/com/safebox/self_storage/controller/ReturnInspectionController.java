package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.CompleteContractRequest;
import com.safebox.self_storage.dto.CompleteReturnInspectionRequest;
import com.safebox.self_storage.dto.CreateReturnScheduleRequest;
import com.safebox.self_storage.dto.response.ContractCompletionResponse;
import com.safebox.self_storage.dto.response.FacilityReturnTaskItemResponse;
import com.safebox.self_storage.dto.response.ReturnInspectionDetailResponse;
import com.safebox.self_storage.dto.response.ReturnScheduleResponse;
import com.safebox.self_storage.service.ReturnInspectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các API của:
 * - UC16: "Đặt lịch trả kho và xác nhận tình trạng sau trả" (BR16.1 – BR16.10).
 * - UC20: "Hoàn tất trả kho, kiểm tra và đóng hợp đồng" (BR20.1 – BR20.5).
 */
@RestController
public class ReturnInspectionController {

    private final ReturnInspectionService returnInspectionService;

    public ReturnInspectionController(ReturnInspectionService returnInspectionService) {
        this.returnInspectionService = returnInspectionService;
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
    // CUSTOMER API: Đặt lịch và quản lý lịch trả kho của tôi (BR16.1, BR16.2)
    // =========================================================================

    /**
     * UC16: Khách hàng đặt lịch hẹn trả kho (BR16.1, BR16.2).
     */
    @PostMapping("/api/customer/rentals/{contractId}/return-schedule")
    public ResponseEntity<ReturnScheduleResponse> scheduleReturn(
            @PathVariable("contractId") UUID contractId,
            @RequestBody CreateReturnScheduleRequest request) {
        UUID customerId = getCurrentUserId();
        ReturnScheduleResponse response = returnInspectionService.scheduleReturnAppointment(
                customerId, contractId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * UC16: Xem thông tin lịch trả kho hiện có của hợp đồng (BR16.2).
     */
    @GetMapping("/api/customer/rentals/{contractId}/return-schedule")
    public ResponseEntity<ReturnScheduleResponse> getReturnSchedule(
            @PathVariable("contractId") UUID contractId) {
        UUID currentUserId = getCurrentUserId();
        ReturnScheduleResponse response = returnInspectionService.getReturnSchedule(
                currentUserId, contractId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC16: Khách hàng hủy lịch hẹn trả kho đang chờ (BR12.5).
     */
    @DeleteMapping("/api/customer/rentals/{contractId}/return-schedule")
    public ResponseEntity<ReturnScheduleResponse> cancelReturn(
            @PathVariable("contractId") UUID contractId) {
        UUID currentUserId = getCurrentUserId();
        ReturnScheduleResponse response = returnInspectionService.cancelReturnAppointment(
                currentUserId, contractId);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // FACILITY STAFF / MANAGER API: Kiểm tra thực tế và hoàn tất nhận lại kho
    // =========================================================================

    /**
     * UC16: Danh sách công việc tiếp nhận trả kho tại cơ sở (BR12.6, BR12.7, BR16.3).
     */
    @GetMapping("/api/facility/returns")
    public ResponseEntity<List<FacilityReturnTaskItemResponse>> getFacilityReturns(
            @RequestParam(value = "facilityId", required = false) UUID facilityId,
            @RequestParam(value = "status", required = false) String status) {
        UUID staffOrManagerId = getCurrentUserId();
        List<FacilityReturnTaskItemResponse> items = returnInspectionService.getFacilityReturnAppointments(
                staffOrManagerId, facilityId, status);
        return ResponseEntity.ok(items);
    }

    /**
     * UC16: Xem chi tiết biên bản nhận lại kho và kiểm tra theo handoverId (BR16.4, BR16.5).
     */
    @GetMapping("/api/facility/returns/{handoverId}")
    public ResponseEntity<ReturnInspectionDetailResponse> getReturnDetail(
            @PathVariable("handoverId") UUID handoverId) {
        UUID staffOrManagerId = getCurrentUserId();
        ReturnInspectionDetailResponse detail = returnInspectionService.getReturnInspectionDetail(
                staffOrManagerId, handoverId);
        return ResponseEntity.ok(detail);
    }

    /**
     * UC20: Xem chi tiết biên bản kiểm tra trả kho theo contractId (BR20.5).
     */
    @GetMapping({"/api/facility/returns/contract/{contractId}", "/api/v1/return-inspections/contract/{contractId}"})
    public ResponseEntity<ReturnInspectionDetailResponse> getReturnInspectionByContract(
            @PathVariable("contractId") UUID contractId) {
        UUID currentUserId = getCurrentUserId();
        ReturnInspectionDetailResponse detail = returnInspectionService.getReturnInspectionByContract(
                currentUserId, contractId);
        return ResponseEntity.ok(detail);
    }

    /**
     * UC16 & UC20: Nhân viên/Quản lý cơ sở xác nhận kiểm tra và hoàn tất nhận lại kho (BR16.3 – BR16.10, BR20.1, BR20.5).
     */
    @PostMapping({"/api/facility/returns/{handoverId}/complete", "/api/facility/returns/complete", "/api/v1/return-inspections/{handoverId}/complete"})
    public ResponseEntity<ReturnInspectionDetailResponse> completeReturnInspection(
            @PathVariable(value = "handoverId", required = false) UUID handoverId,
            @RequestBody CompleteReturnInspectionRequest request) {
        UUID staffOrManagerId = getCurrentUserId();
        if (handoverId != null && request.getHandoverId() == null) {
            request.setHandoverId(handoverId);
        }
        ReturnInspectionDetailResponse response = returnInspectionService.completeReturnHandoverAndInspection(
                staffOrManagerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * UC20: Quản lý/Nhân viên cơ sở hoàn tất thủ tục tất toán tài chính và đóng hợp đồng, giải phóng ô kho (BR20.1 – BR20.5).
     */
    @PostMapping({"/api/facility/contracts/{contractId}/complete-return", "/api/v1/contracts/{contractId}/complete-return"})
    public ResponseEntity<ContractCompletionResponse> completeContract(
            @PathVariable("contractId") UUID contractId,
            @RequestBody(required = false) CompleteContractRequest request) {
        UUID staffOrManagerId = getCurrentUserId();
        if (request == null) {
            request = new CompleteContractRequest();
        }
        ContractCompletionResponse response = returnInspectionService.completeContractAndFinalizeUnit(
                staffOrManagerId, contractId, request);
        return ResponseEntity.ok(response);
    }
}
