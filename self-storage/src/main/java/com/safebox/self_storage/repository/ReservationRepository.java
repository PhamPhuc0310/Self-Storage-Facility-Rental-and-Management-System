package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.Reservation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository truy xuất dữ liệu từ bảng RESERVATIONS.
 */
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Lấy danh sách tất cả yêu cầu đặt kho của một khách hàng, sắp xếp theo thời gian tạo mới nhất.
     *
     * @param customerId Mã định danh UUID của khách hàng
     * @return Danh sách yêu cầu đặt kho
     */
    List<Reservation> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /**
     * Lấy danh sách yêu cầu đặt kho của một khách hàng theo trạng thái cụ thể (PENDING, CONFIRMED,...),
     * sắp xếp theo thời gian tạo mới nhất.
     *
     * @param customerId Mã định danh UUID của khách hàng
     * @param status     Trạng thái yêu cầu
     * @return Danh sách yêu cầu đặt kho
     */
    List<Reservation> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, String status);

    /**
     * Tìm một yêu cầu đặt kho theo ID và ID khách hàng (dùng để kiểm tra quyền sở hữu khi xem/hủy).
     *
     * @param reservationId Mã UUID của yêu cầu
     * @param customerId    Mã UUID của khách hàng
     * @return Optional chứa yêu cầu nếu tìm thấy
     */
    Optional<Reservation> findByReservationIdAndCustomerId(UUID reservationId, UUID customerId);

    /**
     * Lấy danh sách yêu cầu trên toàn hệ thống theo trạng thái (ví dụ: các yêu cầu PENDING cần duyệt).
     *
     * @param status Trạng thái cần tìm
     * @return Danh sách yêu cầu đặt kho
     */
    List<Reservation> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Lấy toàn bộ danh sách yêu cầu đặt kho, sắp xếp từ mới nhất đến cũ nhất.
     *
     * @return Danh sách tất cả yêu cầu đặt kho
     */
    List<Reservation> findAllByOrderByCreatedAtDesc();

    /**
     * Lấy danh sách yêu cầu đặt kho thuộc một cơ sở cụ thể.
     *
     * @param facilityId Mã UUID của cơ sở
     * @return Danh sách yêu cầu đặt kho của cơ sở đó
     */
    List<Reservation> findByFacilityIdOrderByCreatedAtDesc(UUID facilityId);

    /**
     * Lấy danh sách yêu cầu đặt kho thuộc một cơ sở cụ thể và lọc theo trạng thái.
     *
     * @param facilityId Mã UUID của cơ sở
     * @param status     Trạng thái yêu cầu
     * @return Danh sách yêu cầu đặt kho thỏa điều kiện
     */
    List<Reservation> findByFacilityIdAndStatusOrderByCreatedAtDesc(UUID facilityId, String status);

    /**
     * Đếm số lượng yêu cầu của khách hàng theo trạng thái.
     *
     * @param customerId Mã khách hàng
     * @param status     Trạng thái yêu cầu
     * @return Số lượng yêu cầu
     */
    long countByCustomerIdAndStatus(UUID customerId, String status);

    /**
     * Đếm tổng số lượng yêu cầu trên toàn hệ thống theo trạng thái.
     *
     * @param status Trạng thái yêu cầu
     * @return Tổng số lượng yêu cầu
     */
    long countByStatus(String status);

    /**
     * Tìm kiếm linh hoạt các yêu cầu đặt kho với nhiều tiêu chí tùy chọn (khách hàng, trạng thái, cơ sở).
     *
     * @param customerId (Tùy chọn) Mã khách hàng
     * @param status     (Tùy chọn) Trạng thái yêu cầu
     * @param facilityId (Tùy chọn) Mã cơ sở
     * @return Danh sách yêu cầu đặt kho thỏa điều kiện
     */
    @Query("SELECT r FROM Reservation r WHERE " +
           "(:customerId IS NULL OR r.customerId = :customerId) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:facilityId IS NULL OR r.facilityId = :facilityId) " +
           "ORDER BY r.createdAt DESC")
    List<Reservation> searchReservations(@Param("customerId") UUID customerId,
                                         @Param("status") String status,
                                         @Param("facilityId") UUID facilityId);

    /**
     * Đếm số lượng yêu cầu đã xác nhận (CONFIRMED) trùng lặp khoảng thời gian tại cơ sở và loại kho.
     * Phục vụ tính khả dụng kho theo thời gian (UC03).
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.facilityId = :facilityId AND r.typeId = :typeId "
            + "AND r.status = 'CONFIRMED' AND r.startDate < :endDate AND r.endDate > :startDate")
    long countConfirmedOverlapping(@Param("facilityId") UUID facilityId, @Param("typeId") Integer typeId,
                                   @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
