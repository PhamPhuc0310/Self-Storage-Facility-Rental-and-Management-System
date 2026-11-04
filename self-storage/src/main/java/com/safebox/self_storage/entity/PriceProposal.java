package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PRICE_ADJUSTMENT_PROPOSALS")
public class PriceProposal {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "proposal_id") private UUID proposalId;
    @Column(name = "facility_id", nullable = false) private UUID facilityId;
    @Column(name = "type_id", nullable = false) private Integer typeId;
    @Column(name = "proposed_monthly_price", nullable = false, precision = 18, scale = 2) private BigDecimal proposedMonthlyPrice;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "reason", nullable = false, length = 500) private String reason;
    @Column(name = "status", nullable = false, length = 20) private String status;
    @Column(name = "proposed_by", nullable = false) private UUID proposedBy;
    @Column(name = "proposed_at", nullable = false) private LocalDateTime proposedAt;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "pricing_id") private UUID pricingId;
    public UUID getProposalId() { return proposalId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { facilityId=v; }
    public Integer getTypeId() { return typeId; }
    public void setTypeId(Integer v) { typeId=v; }
    public BigDecimal getProposedMonthlyPrice() { return proposedMonthlyPrice; }
    public void setProposedMonthlyPrice(BigDecimal v) { proposedMonthlyPrice=v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { effectiveFrom=v; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate v) { effectiveTo=v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason=v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status=v; }
    public UUID getProposedBy() { return proposedBy; }
    public void setProposedBy(UUID v) { proposedBy=v; }
    public LocalDateTime getProposedAt() { return proposedAt; }
    public void setProposedAt(LocalDateTime v) { proposedAt=v; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID v) { reviewedBy=v; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime v) { reviewedAt=v; }
    public UUID getPricingId() { return pricingId; }
    public void setPricingId(UUID v) { pricingId=v; }
}
