package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.contract.AssignableUnitResponse;
import com.safebox.self_storage.dto.contract.ConfirmContractRequest;
import com.safebox.self_storage.dto.contract.ContractResponse;
import com.safebox.self_storage.dto.contract.CreateContractRequest;

import java.util.List;
import java.util.UUID;

/**
 * Interface định nghĩa các nghiệp vụ xử lý hợp đồng thuê kho:
 * - UC09: Gán kho vật lý và lập hợp đồng thuê (FACILITY_MANAGER)
 * - UC10: Xem và xác nhận hợp đồng thuê (CUSTOMER)
 */
public interface ContractService {

    // =========================================================================
    // UC09: Dành cho Quản lý cơ sở (FACILITY_MANAGER)
    // =========================================================================

    /**
     * Lấy danh sách các ô kho vật lý khả dụng (AVAILABLE và không trùng lịch thuê)
     * để quản lý lựa chọn gán cho yêu cầu đặt kho đã CONFIRMED (BR09.2, BR09.9).
     *
     * @param managerId     UUID của quản lý cơ sở đăng nhập
     * @param reservationId UUID của yêu cầu đặt kho
     * @return Danh sách ô kho vật lý phù hợp
     */
    List<AssignableUnitResponse> getAssignableUnits(UUID managerId, UUID reservationId);

    /**
     * Gán kho vật lý và lập hợp đồng thuê cho yêu cầu đặt kho đã CONFIRMED (UC09).
     *
     * @param managerId     UUID của quản lý cơ sở lập hợp đồng (BR09.1)
     * @param reservationId UUID của yêu cầu đặt kho
     * @param request       DTO chứa thông tin ô kho gán, điều khoản và ghi chú
     * @return DTO chứa thông tin hợp đồng vừa lập
     */
    ContractResponse createContract(UUID managerId, UUID reservationId, CreateContractRequest request);

    /**
     * Xem hợp đồng của yêu cầu đặt kho từ góc nhìn quản lý cơ sở.
     *
     * @param managerId     UUID của quản lý cơ sở
     * @param reservationId UUID của yêu cầu đặt kho
     * @return DTO thông tin hợp đồng
     */
    ContractResponse getContractByReservationForManager(UUID managerId, UUID reservationId);

    /**
     * Xem chi tiết hợp đồng theo mã hợp đồng từ góc nhìn quản lý cơ sở.
     *
     * @param managerId  UUID của quản lý cơ sở
     * @param contractId UUID của hợp đồng
     * @return DTO thông tin hợp đồng
     */
    ContractResponse getContractDetailForManager(UUID managerId, UUID contractId);

    // =========================================================================
    // UC10: Dành cho Khách hàng (CUSTOMER)
    // =========================================================================

    /**
     * Khách hàng lấy danh sách tất cả các hợp đồng thuê kho của chính mình (BR10.1).
     *
     * @param customerId UUID của khách hàng từ JWT
     * @param status     (Tùy chọn) Lọc theo trạng thái hợp đồng hoặc ALL/null
     * @return Danh sách hợp đồng của khách hàng
     */
    List<ContractResponse> getMyContracts(UUID customerId, String status);

    /**
     * Khách hàng xem chi tiết một hợp đồng cụ thể của mình (BR10.1, BR10.2).
     *
     * @param customerId UUID của khách hàng từ JWT
     * @param contractId UUID của hợp đồng
     * @return DTO thông tin hợp đồng
     */
    ContractResponse getMyContractDetail(UUID customerId, UUID contractId);

    /**
     * Khách hàng xem hợp đồng liên kết với mã yêu cầu đặt kho của mình (UC10).
     *
     * @param customerId    UUID của khách hàng từ JWT
     * @param reservationId UUID của yêu cầu đặt kho
     * @return DTO thông tin hợp đồng
     */
    ContractResponse getMyContractByReservation(UUID customerId, UUID reservationId);

    /**
     * Khách hàng xác nhận hợp đồng thuê trực tuyến (BR10.3, BR10.4, BR10.5).
     *
     * @param customerId UUID của khách hàng từ JWT
     * @param contractId UUID của hợp đồng
     * @param request    DTO xác nhận đồng ý điều khoản
     * @return DTO hợp đồng sau khi đã chuyển sang trạng thái PENDING_HANDOVER
     */
    ContractResponse confirmContract(UUID customerId, UUID contractId, ConfirmContractRequest request);
}
