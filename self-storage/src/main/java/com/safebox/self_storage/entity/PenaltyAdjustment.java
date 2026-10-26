package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng PENALTY_ADJUSTMENTS (UC18: BR18.5).
 * - BR18.5: Không tự động xóa khoản nợ khi chỉnh ngày hợp đồng; phải có nghiệp vụ điều chỉnh có lưu vết.
 */
@Entity
@Table(name = "PENALTY_ADJUSTMENTS")
public class PenaltyAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "adjustment_id", updatable = false, nullable = false)
    private UUID adjustmentId;

    @Column(name = "penalty_id", nullable = false)
    private UUID penaltyId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "original_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "adjusted_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal adjustedAmount;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountAmount;

    /**
     * Loại điều chỉnh:
     * - WAIVER: Miễn giảm toàn bộ
     * - PARTIAL_DISCOUNT: Giảm trừ một phần
     * - CORRECTION: Hiệu chỉnh sai sót
     */
    @Column(name = "adjustment_type", nullable = false, length = 30)
    private String adjustmentType;

    /** Lý do điều chỉnh (bắt buộc theo BR18.5) */
    @Column(name = "reason", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String reason;

    @Column(name = "adjusted_by", nullable = false)
    private UUID adjustedBy;

    @Column(name = "adjusted_at", nullable = false)
    private LocalDateTime adjustedAt = LocalDateTime.now();

    @Column(name = "note", columnDefinition = "NVARCHAR(MAX)")
    private String note;

    public PenaltyAdjustment() {
    }

    public UUID getAdjustmentId() {
        return adjustmentId;
    }

    public void setAdjustmentId(UUID adjustmentId) {
        this.adjustmentId = adjustmentId;
    }

    public UUID getPenaltyId() {
        return penaltyId;
    }

    public void setPenaltyId(UUID penaltyId) {
        this.penaltyId = penaltyId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public BigDecimal getAdjustedAmount() {
        return adjustedAmount;
    }

    public void setAdjustedAmount(BigDecimal adjustedAmount) {
        this.adjustedAmount = adjustedAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(String adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getAdjustedBy() {
        return adjustedBy;
    }

    public void setAdjustedBy(UUID adjustedBy) {
        this.adjustedBy = adjustedBy;
    }

    public LocalDateTime getAdjustedAt() {
        return adjustedAt;
    }

    public void setAdjustedAt(LocalDateTime adjustedAt) {
        this.adjustedAt = adjustedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
