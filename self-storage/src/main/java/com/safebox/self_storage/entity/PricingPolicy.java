package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "PRICING_POLICIES")
public class PricingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pricing_id", updatable = false, nullable = false)
    private UUID pricingId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "monthly_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyPrice;
    
    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "daily_overdue_rate", nullable = false, precision = 18, scale = 2)
    private BigDecimal dailyOverdueRate;
    @Column(name = "late_payment_rate", nullable = false, precision = 18, scale = 2)
    private BigDecimal latePaymentRate;
    @Column(name = "early_termination_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal earlyTerminationFee;
    @Column(name = "fee_waiver_allowed", nullable = false)
    private boolean feeWaiverAllowed;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    public UUID getPricingId() { return pricingId; }
    public void setPricingId(UUID pricingId) { this.pricingId = pricingId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public Integer getTypeId() { return typeId; }
    public void setTypeId(Integer typeId) { this.typeId = typeId; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public BigDecimal getDailyOverdueRate() { return dailyOverdueRate; }
    public void setDailyOverdueRate(BigDecimal value) { this.dailyOverdueRate = value; }
    public BigDecimal getLatePaymentRate() { return latePaymentRate; }
    public void setLatePaymentRate(BigDecimal value) { this.latePaymentRate = value; }
    public BigDecimal getEarlyTerminationFee() { return earlyTerminationFee; }
    public void setEarlyTerminationFee(BigDecimal value) { this.earlyTerminationFee = value; }
    public boolean isFeeWaiverAllowed() { return feeWaiverAllowed; }
    public void setFeeWaiverAllowed(boolean value) { this.feeWaiverAllowed = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
}
