package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.contract.ConfirmContractRequest;
import com.safebox.self_storage.dto.contract.ContractResponse;
import com.safebox.self_storage.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các nghiệp vụ hợp đồng dành cho Khách hàng (CUSTOMER):
 * - UC10: Xem và xác nhận hợp đồng thuê kho
 */
@RestController
@RequestMapping("/api/contracts/my")
public class CustomerContractController {

    private final ContractService contractService;

    public CustomerContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    /**
     * Chức năng UC10 (BR10.1): Lấy danh sách hợp đồng thuê của khách hàng đang đăng nhập.
     *
     * Endpoint: GET /api/contracts/my
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param customerId UUID của khách hàng lấy từ JWT token
     * @param status     (Tùy chọn) Lọc theo trạng thái hợp đồng (PENDING_CONFIRMATION, PENDING_HANDOVER, ACTIVE,...)
     * @return Danh sách hợp đồng thuê kho
     */
    @GetMapping
    public ResponseEntity<List<ContractResponse>> getMyContracts(
            @AuthenticationPrincipal UUID customerId,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(contractService.getMyContracts(customerId, status));
    }

    /**
     * Chức năng UC10 (BR10.1, BR10.2): Xem thông tin chi tiết một hợp đồng thuê của chính mình.
     *
     * Endpoint: GET /api/contracts/my/{contractId}
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param customerId UUID của khách hàng từ JWT token
     * @param contractId UUID của hợp đồng cần xem
     * @return Chi tiết hợp đồng gồm cơ sở, ô kho, giá thuê, tiền cọc, khoản phải trả và điều khoản
     */
    @GetMapping("/{contractId}")
    public ResponseEntity<ContractResponse> getMyContractDetail(
            @AuthenticationPrincipal UUID customerId,
            @PathVariable UUID contractId) {
        return ResponseEntity.ok(contractService.getMyContractDetail(customerId, contractId));
    }

    /**
     * Chức năng UC10: Xem hợp đồng liên kết với yêu cầu đặt kho của mình.
     *
     * Endpoint: GET /api/contracts/my/by-reservation/{reservationId}
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param customerId    UUID của khách hàng từ JWT token
     * @param reservationId UUID của yêu cầu đặt kho
     * @return Chi tiết hợp đồng tương ứng
     */
    @GetMapping("/by-reservation/{reservationId}")
    public ResponseEntity<ContractResponse> getMyContractByReservation(
            @AuthenticationPrincipal UUID customerId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(contractService.getMyContractByReservation(customerId, reservationId));
    }

    /**
     * Chức năng UC10 (BR10.3 - BR10.8): Khách hàng ký xác nhận điện tử hợp đồng thuê kho.
     * Sau khi ký, trạng thái hợp đồng chuyển sang PENDING_HANDOVER (Chờ bàn giao).
     *
     * Endpoint: POST /api/contracts/my/{contractId}/confirm
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param customerId UUID của khách hàng từ JWT token
     * @param contractId UUID của hợp đồng cần xác nhận
     * @param request    DTO xác nhận đồng ý điều khoản
     * @return Chi tiết hợp đồng sau khi đã cập nhật trạng thái PENDING_HANDOVER
     */
    @PostMapping("/{contractId}/confirm")
    public ResponseEntity<ContractResponse> confirmContract(
            @AuthenticationPrincipal UUID customerId,
            @PathVariable UUID contractId,
            @Valid @RequestBody ConfirmContractRequest request) {
        return ResponseEntity.ok(contractService.confirmContract(customerId, contractId, request));
    }
}
