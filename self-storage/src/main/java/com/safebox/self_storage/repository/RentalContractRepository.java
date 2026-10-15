package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.RentalContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository truy xuất dữ liệu từ bảng RENTAL_CONTRACTS.
 */
@Repository
public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {

    List<RentalContract> findByCustomerIdOrderByStartDateDesc(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RentalContract c where c.contractId = :id")
    Optional<RentalContract> lockById(@Param("id") UUID id);

    Optional<RentalContract> findByContractId(UUID contractId);

    Optional<RentalContract> findByContractIdAndCustomerId(UUID contractId, UUID customerId);

    Optional<RentalContract> findByContractNumber(String contractNumber);

    Optional<RentalContract> findByReservationId(UUID reservationId);

    List<RentalContract> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<RentalContract> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, String status);

    Page<RentalContract> findByCustomerId(UUID customerId, Pageable pageable);

    Page<RentalContract> findByCustomerIdAndStatus(UUID customerId, String status, Pageable pageable);

    Page<RentalContract> findByCustomerIdAndStatusIn(UUID customerId, Collection<String> statuses, Pageable pageable);

    @Query("SELECT c FROM RentalContract c WHERE c.customerId = :customerId "
            + "AND (:statuses IS NULL OR c.status IN :statuses) "
            + "AND (:search IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "ORDER BY c.createdAt DESC")
    Page<RentalContract> findCustomerContractsWithFilter(
            @Param("customerId") UUID customerId,
            @Param("statuses") Collection<String> statuses,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT c FROM RentalContract c JOIN StorageUnit u ON c.unitId = u.unitId "
            + "WHERE u.facilityId = :facilityId "
            + "AND (:statuses IS NULL OR c.status IN :statuses) "
            + "AND (:search IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "     OR LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "ORDER BY c.createdAt DESC")
    Page<RentalContract> findFacilityContractsWithFilter(
            @Param("facilityId") UUID facilityId,
            @Param("statuses") Collection<String> statuses,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT c FROM RentalContract c JOIN StorageUnit u ON c.unitId = u.unitId "
            + "WHERE c.contractId = :contractId AND u.facilityId = :facilityId")
    Optional<RentalContract> findByContractIdAndFacilityId(
            @Param("contractId") UUID contractId,
            @Param("facilityId") UUID facilityId);

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
