package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.contract.AssignableUnitResponse;
import com.safebox.self_storage.dto.contract.ContractResponse;
import com.safebox.self_storage.dto.contract.CreateContractRequest;
import com.safebox.self_storage.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các nghiệp vụ hợp đồng dành cho Quản lý cơ sở (FACILITY_MANAGER):
 * - UC09: Gán kho vật lý và lập hợp đồng thuê
 */
@RestController
@RequestMapping("/api/manager")
public class ManagerContractController {

    private final ContractService contractService;

    public ManagerContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    /**
     * Chức năng UC09 (BR09.9): Lấy danh sách ô kho vật lý khả dụng để gán cho đơn đặt kho đã CONFIRMED.
     *
     * Endpoint: GET /api/manager/reservations/{reservationId}/assignable-units
     * Quyền truy cập: Quản lý cơ sở (ROLE_FACILITY_MANAGER)
     *
     * @param managerId     UUID của quản lý cơ sở từ token
     * @param reservationId UUID của yêu cầu đặt kho
     * @return Danh sách ô kho vật lý phù hợp (AVAILABLE và không trùng lịch thuê)
     */
    @GetMapping("/reservations/{reservationId}/assignable-units")
    public ResponseEntity<List<AssignableUnitResponse>> getAssignableUnits(
            @AuthenticationPrincipal UUID managerId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(contractService.getAssignableUnits(managerId, reservationId));
    }

    /**
     * Chức năng UC09: Gán kho vật lý và lập hợp đồng thuê kho mới.
     *
     * Endpoint: POST /api/manager/reservations/{reservationId}/contract
     * Quyền truy cập: Quản lý cơ sở (ROLE_FACILITY_MANAGER)
     *
     * @param managerId     UUID của quản lý cơ sở từ token
     * @param reservationId UUID của yêu cầu đặt kho
     * @param request       DTO chứa mã kho vật lý, điều khoản và ghi chú
     * @return Hợp đồng vừa tạo với mã HTTP 201 Created
     */
    @PostMapping("/reservations/{reservationId}/contract")
    public ResponseEntity<ContractResponse> createContract(
            @AuthenticationPrincipal UUID managerId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody CreateContractRequest request) {
        ContractResponse response = contractService.createContract(managerId, reservationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Xem hợp đồng liên kết với yêu cầu đặt kho từ góc nhìn quản lý cơ sở.
     *
     * Endpoint: GET /api/manager/reservations/{reservationId}/contract
     * Quyền truy cập: Quản lý cơ sở (ROLE_FACILITY_MANAGER)
     */
    @GetMapping("/reservations/{reservationId}/contract")
    public ResponseEntity<ContractResponse> getContractByReservation(
            @AuthenticationPrincipal UUID managerId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(contractService.getContractByReservationForManager(managerId, reservationId));
    }

    /**
     * Xem chi tiết hợp đồng theo mã hợp đồng từ góc nhìn quản lý cơ sở.
     *
     * Endpoint: GET /api/manager/contracts/{contractId}
     * Quyền truy cập: Quản lý cơ sở (ROLE_FACILITY_MANAGER)
     */
    @GetMapping("/contracts/{contractId}")
    public ResponseEntity<ContractResponse> getContractDetail(
            @AuthenticationPrincipal UUID managerId,
            @PathVariable UUID contractId) {
        return ResponseEntity.ok(contractService.getContractDetailForManager(managerId, contractId));
    }
}
