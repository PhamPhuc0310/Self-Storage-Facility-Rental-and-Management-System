package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng RESERVATIONS trong cơ sở dữ liệu.
 * Quản lý thông tin yêu cầu đặt kho của khách hàng:
 * - Mã yêu cầu (UUID)
 * - Khách hàng, cơ sở, loại kho, bảng giá áp dụng
 * - Thời gian thuê, số tiền dự kiến, thời hạn giữ chỗ
 * - Trạng thái xử lý (PENDING, CONFIRMED, REJECTED, CANCELLED)
 */
@Entity
@Table(name = "RESERVATIONS")
public class Reservation {

    /** Mã định danh duy nhất của yêu cầu đặt kho (Khóa chính) */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reservation_id", updatable = false, nullable = false)
    private UUID reservationId;

    /** Mã khách hàng thực hiện yêu cầu (Khóa ngoại tham chiếu USERS) */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Mã cơ sở được chọn thuê (Khóa ngoại tham chiếu FACILITIES) */
    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    /** Mã loại kho được chọn thuê (Khóa ngoại tham chiếu STORAGE_UNIT_TYPES) */
    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    /** Mã chính sách giá áp dụng (Khóa ngoại tham chiếu PRICING_POLICIES) */
    @Column(name = "pricing_id", nullable = false)
    private UUID pricingId;

    /** Ngày bắt đầu thuê dự kiến */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Ngày kết thúc thuê dự kiến */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Thời điểm hết hạn giữ chỗ (nếu không được xử lý hoặc thanh toán) */
    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    /** Ước tính tổng tiền thuê kho (giá tháng x số tháng) */
    @Column(name = "estimated_rental_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal estimatedRentalAmount;

    /** Ước tính số tiền đặt cọc cần đóng */
    @Column(name = "estimated_deposit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal estimatedDepositAmount;

    /** Trạng thái yêu cầu: PENDING, CONFIRMED, REJECTED, CANCELLED */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    /** Thời điểm khách hàng gửi yêu cầu */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Thời điểm yêu cầu bị hủy hoặc bị từ chối */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Lý do khách hàng hủy hoặc ban quản lý từ chối */
    @Column(name = "cancellation_reason")
    private String cancellationReason;

    public Reservation() {
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public Integer getTypeId() {
        return typeId;
    }

    public void setTypeId(Integer typeId) {
        this.typeId = typeId;
    }

    public UUID getPricingId() {
        return pricingId;
    }

    public void setPricingId(UUID pricingId) {
        this.pricingId = pricingId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDateTime getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public void setHoldExpiresAt(LocalDateTime holdExpiresAt) {
        this.holdExpiresAt = holdExpiresAt;
    }

    public BigDecimal getEstimatedRentalAmount() {
        return estimatedRentalAmount;
    }

    public void setEstimatedRentalAmount(BigDecimal estimatedRentalAmount) {
        this.estimatedRentalAmount = estimatedRentalAmount;
    }

    public BigDecimal getEstimatedDepositAmount() {
        return estimatedDepositAmount;
    }

    public void setEstimatedDepositAmount(BigDecimal estimatedDepositAmount) {
        this.estimatedDepositAmount = estimatedDepositAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }
}
