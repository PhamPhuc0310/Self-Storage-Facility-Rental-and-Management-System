package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.RentalPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository truy xuất dữ liệu từ bảng RENTAL_POLICIES (Chính sách thuê kho).
 */
public interface RentalPolicyRepository extends JpaRepository<RentalPolicy, UUID> {

    /**
     * Tìm chính sách thuê đang có hiệu lực gần nhất của một cơ sở theo trạng thái (thường là ACTIVE)
     * để lấy thông tin số phút giữ chỗ đặt kho (reservation_hold_minutes).
     *
     * @param facilityId Mã UUID của cơ sở
     * @param status     Trạng thái chính sách (ví dụ: "ACTIVE")
     * @return Optional chứa chính sách thuê nếu tìm thấy
     */
    Optional<RentalPolicy> findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(UUID facilityId, String status);
}
