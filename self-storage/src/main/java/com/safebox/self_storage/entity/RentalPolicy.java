package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng RENTAL_POLICIES trong cơ sở dữ liệu.
 * Quản lý các chính sách thuê kho của cơ sở:
 * - Số ngày mở gia hạn (renewal_open_days)
 * - Hạn ưu tiên (priority_deadline_days)
 * - Số giờ thanh toán gia hạn (renewal_payment_hours)
 * - Số phút giữ chỗ khi đặt kho (reservation_hold_minutes)
 * - Thời gian ân hạn quá hạn (overdue_grace_days)
 */
@Entity
@Table(name = "RENTAL_POLICIES")
public class RentalPolicy {

    /** Mã định danh duy nhất của chính sách (Khóa chính) */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "policy_id", updatable = false, nullable = false)
    private UUID policyId;

    /** Mã cơ sở áp dụng (null nếu áp dụng chung toàn hệ thống) */
    @Column(name = "facility_id")
    private UUID facilityId;

    /** Số ngày mở đăng ký gia hạn trước khi hết hạn hợp đồng */
    @Column(name = "renewal_open_days", nullable = false)
    private Integer renewalOpenDays;

    /** Số ngày hạn chót ưu tiên gia hạn */
    @Column(name = "priority_deadline_days", nullable = false)
    private Integer priorityDeadlineDays;

    /** Số giờ tối đa để thanh toán gia hạn hợp đồng */
    @Column(name = "renewal_payment_hours", nullable = false)
    private Integer renewalPaymentHours;

    /** Số phút tối đa giữ chỗ cho yêu cầu đặt kho (reservation hold) */
    @Column(name = "reservation_hold_minutes", nullable = false)
    private Integer reservationHoldMinutes;

    /** Số ngày ân hạn khi hợp đồng quá hạn */
    @Column(name = "overdue_grace_days", nullable = false)
    private Integer overdueGraceDays;

    /** Quy định và điều khoản hủy thuê */
    @Column(name = "cancellation_policy")
    private String cancellationPolicy;

    /** Quy định và điều khoản trả kho */
    @Column(name = "return_policy")
    private String returnPolicy;

    /** Ngày chính sách bắt đầu có hiệu lực */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Ngày chính sách hết hiệu lực (null nếu còn hiệu lực vô thời hạn) */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** Trạng thái chính sách (ví dụ: ACTIVE, INACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public RentalPolicy() {
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public Integer getRenewalOpenDays() {
        return renewalOpenDays;
    }

    public void setRenewalOpenDays(Integer renewalOpenDays) {
        this.renewalOpenDays = renewalOpenDays;
    }

    public Integer getPriorityDeadlineDays() {
        return priorityDeadlineDays;
    }

    public void setPriorityDeadlineDays(Integer priorityDeadlineDays) {
        this.priorityDeadlineDays = priorityDeadlineDays;
    }

    public Integer getRenewalPaymentHours() {
        return renewalPaymentHours;
    }

    public void setRenewalPaymentHours(Integer renewalPaymentHours) {
        this.renewalPaymentHours = renewalPaymentHours;
    }

    public Integer getReservationHoldMinutes() {
        return reservationHoldMinutes;
    }

    public void setReservationHoldMinutes(Integer reservationHoldMinutes) {
        this.reservationHoldMinutes = reservationHoldMinutes;
    }

    public Integer getOverdueGraceDays() {
        return overdueGraceDays;
    }

    public void setOverdueGraceDays(Integer overdueGraceDays) {
        this.overdueGraceDays = overdueGraceDays;
    }

    public String getCancellationPolicy() {
        return cancellationPolicy;
    }

    public void setCancellationPolicy(String cancellationPolicy) {
        this.cancellationPolicy = cancellationPolicy;
    }

    public String getReturnPolicy() {
        return returnPolicy;
    }

    public void setReturnPolicy(String returnPolicy) {
        this.returnPolicy = returnPolicy;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
