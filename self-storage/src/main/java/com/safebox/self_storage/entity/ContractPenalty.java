package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng CONTRACT_PENALTIES (UC18: Theo dõi quá hạn và xử lý phí phát sinh).
 * - BR18.1: Chỉ đánh dấu quá hạn khi quá thời điểm kết thúc và chưa có gia hạn hợp lệ / đã hoàn tất trả kho.
 * - BR18.2: Công thức phí quá hạn có cấu hình rõ ràng (RentalPolicy).
 * - BR18.3: Mỗi kỳ phí chỉ phát sinh một lần (Idempotent), có log và giải thích cách tính.
 */
@Entity
@Table(name = "CONTRACT_PENALTIES")
public class ContractPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "penalty_id", updatable = false, nullable = false)
    private UUID penaltyId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(name = "overdue_days", nullable = false)
    private Integer overdueDays;

    @Column(name = "billable_days", nullable = false)
    private Integer billableDays;

    @Column(name = "daily_rate", nullable = false, precision = 18, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "penalty_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal penaltyRate;

    @Column(name = "penalty_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "calculation_explanation", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String calculationExplanation;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    /**
     * Trạng thái:
     * - UNPAID: Chưa thanh toán
     * - PAID: Đã thanh toán đầy đủ
     * - PARTIAL: Đã thanh toán một phần
     * - ADJUSTED: Đã điều chỉnh / miễn giảm
     * - CANCELLED: Đã hủy
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "UNPAID";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ContractPenalty() {
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

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public LocalDate getPeriodDate() {
        return periodDate;
    }

    public void setPeriodDate(LocalDate periodDate) {
        this.periodDate = periodDate;
    }

    public Integer getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(Integer overdueDays) {
        this.overdueDays = overdueDays;
    }

    public Integer getBillableDays() {
        return billableDays;
    }

    public void setBillableDays(Integer billableDays) {
        this.billableDays = billableDays;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }

    public void setDailyRate(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    public BigDecimal getPenaltyRate() {
        return penaltyRate;
    }

    public void setPenaltyRate(BigDecimal penaltyRate) {
        this.penaltyRate = penaltyRate;
    }

    public BigDecimal getPenaltyAmount() {
        return penaltyAmount;
    }

    public void setPenaltyAmount(BigDecimal penaltyAmount) {
        this.penaltyAmount = penaltyAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getCalculationExplanation() {
        return calculationExplanation;
    }

    public void setCalculationExplanation(String calculationExplanation) {
        this.calculationExplanation = calculationExplanation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
