package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.RentalPolicy;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

/**
 * Repository truy xuất dữ liệu từ bảng RENTAL_POLICIES (Chính sách thuê kho).
 */
public interface RentalPolicyRepository extends JpaRepository<RentalPolicy, UUID> {
    List<RentalPolicy> findByFacilityId(UUID facilityId);

    Optional<RentalPolicy> findFirstByFacilityIdIsNullOrderByEffectiveFromAscPolicyIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RentalPolicy p where p.policyId = :id")
    Optional<RentalPolicy> lockById(@Param("id") UUID id);

    /**
     * Tìm chính sách thuê đang có hiệu lực gần nhất của một cơ sở theo trạng thái (thường là ACTIVE)
     * để lấy thông tin số phút giữ chỗ đặt kho (reservation_hold_minutes).
     *
     * @param facilityId Mã UUID của cơ sở
     * @param status     Trạng thái chính sách (ví dụ: "ACTIVE")
     * @return Optional chứa chính sách thuê nếu tìm thấy
     */
    Optional<RentalPolicy> findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(UUID facilityId, String status);

    Optional<RentalPolicy> findFirstByFacilityIdIsNullAndStatusOrderByEffectiveFromDesc(String status);

    Optional<RentalPolicy> findFirstByStatusOrderByEffectiveFromDesc(String status);

    @Query("select p from RentalPolicy p where p.status = 'ACTIVE' "
            + "and p.overdueApprovedAt is not null and p.overdueApprovedBy is not null "
            + "and (p.facilityId = :facilityId or p.facilityId is null) "
            + "and p.effectiveFrom <= :onDate and (p.effectiveTo is null or p.effectiveTo >= :onDate)")
    List<RentalPolicy> findApplicableOverduePolicies(@Param("facilityId") UUID facilityId,
                                                     @Param("onDate") LocalDate onDate);
}
