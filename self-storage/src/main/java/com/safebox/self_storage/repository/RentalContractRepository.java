package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.RentalContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.*;

/**
 * Repository truy xuất dữ liệu từ bảng RENTAL_CONTRACTS.
 */
public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {

    /**
     * Tìm hợp đồng theo mã đặt kho liên kết.
     */
    Optional<RentalContract> findByReservationId(UUID reservationId);

    /**
     * Tìm hợp đồng theo mã hợp đồng công khai (contract_number).
     */
    Optional<RentalContract> findByContractNumber(String contractNumber);

    /**
     * Tìm hợp đồng theo ID và mã khách hàng (đảm bảo quyền sở hữu của khách hàng - BR10.1, BR10.9).
     */
    Optional<RentalContract> findByContractIdAndCustomerId(UUID contractId, UUID customerId);

    /**
     * Lấy toàn bộ danh sách hợp đồng của khách hàng, sắp xếp theo thời gian tạo mới nhất.
     */
    List<RentalContract> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /**
     * Lấy danh sách hợp đồng của khách hàng theo trạng thái, sắp xếp mới nhất.
     */
    List<RentalContract> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, String status);

    /**
     * Kiểm tra xem đã có hợp đồng chưa hủy cho yêu cầu đặt kho này chưa (BR09.3).
     */
    @Query("SELECT c FROM RentalContract c WHERE c.reservationId = :reservationId AND c.status != 'CANCELLED'")
    Optional<RentalContract> findActiveOrPendingContractByReservationId(@Param("reservationId") UUID reservationId);

    /**
     * Đếm số hợp đồng bị trùng lặp thời gian thuê của một ô kho cụ thể (BR09.2, BR09.4).
     * Hai lịch trùng nhau khi: ngày bắt đầu mới < ngày kết thúc cũ VÀ ngày kết thúc mới > ngày bắt đầu cũ.
     */
    @Query("SELECT COUNT(c) FROM RentalContract c WHERE c.unitId = :unitId " +
           "AND c.status NOT IN ('CANCELLED', 'TERMINATED') " +
           "AND c.startDate < :endDate AND c.endDate > :startDate")
    long countOverlappingContractsForUnit(@Param("unitId") UUID unitId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    /**
     * Tìm tập hợp các mã unitId trong danh sách đang bị trùng lịch thuê trong khoảng thời gian (BR09.2, BR09.4).
     */
    @Query("SELECT DISTINCT c.unitId FROM RentalContract c WHERE c.unitId IN :unitIds " +
           "AND c.status NOT IN ('CANCELLED', 'TERMINATED') " +
           "AND c.startDate < :endDate AND c.endDate > :startDate")
    Set<UUID> findOverlappingUnitIds(@Param("unitIds") Collection<UUID> unitIds,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);

    /**
     * Kiểm tra sự tồn tại của contract_number để đảm bảo tính duy nhất.
     */
    boolean existsByContractNumber(String contractNumber);
}
