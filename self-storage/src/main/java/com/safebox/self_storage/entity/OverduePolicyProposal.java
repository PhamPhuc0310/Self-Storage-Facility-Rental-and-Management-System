package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="OVERDUE_POLICY_PROPOSALS")
public class OverduePolicyProposal {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="proposal_id") private UUID proposalId;
    @Column(name="facility_id",nullable=false) private UUID facilityId;
    @Column(name="base_policy_id",nullable=false) private UUID basePolicyId;
    @Column(name="overdue_grace_days",nullable=false) private Integer overdueGraceDays;
    @Column(name="penalty_type",nullable=false,length=50) private String penaltyType;
    @Column(name="daily_penalty_rate",nullable=false,precision=5,scale=4) private BigDecimal dailyPenaltyRate;
    @Column(name="fixed_penalty_amount",nullable=false,precision=18,scale=2) private BigDecimal fixedPenaltyAmount;
    @Column(name="effective_from",nullable=false) private LocalDate effectiveFrom;
    @Column(name="effective_to") private LocalDate effectiveTo;
    @Column(name="reason",nullable=false,length=500) private String reason;
    @Column(name="status",nullable=false,length=20) private String status;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="submitted_at") private LocalDateTime submittedAt;
    @Column(name="withdrawn_at") private LocalDateTime withdrawnAt;
    @Column(name="reviewed_by") private UUID reviewedBy;
    @Column(name="reviewed_at") private LocalDateTime reviewedAt;
    @Column(name="decision_reason",length=500) private String decisionReason;
    @Column(name="approved_policy_id") private UUID approvedPolicyId;
    public UUID getProposalId(){return proposalId;}
    public UUID getFacilityId(){return facilityId;} public void setFacilityId(UUID v){facilityId=v;}
    public UUID getBasePolicyId(){return basePolicyId;} public void setBasePolicyId(UUID v){basePolicyId=v;}
    public Integer getOverdueGraceDays(){return overdueGraceDays;} public void setOverdueGraceDays(Integer v){overdueGraceDays=v;}
    public String getPenaltyType(){return penaltyType;} public void setPenaltyType(String v){penaltyType=v;}
    public BigDecimal getDailyPenaltyRate(){return dailyPenaltyRate;} public void setDailyPenaltyRate(BigDecimal v){dailyPenaltyRate=v;}
    public BigDecimal getFixedPenaltyAmount(){return fixedPenaltyAmount;} public void setFixedPenaltyAmount(BigDecimal v){fixedPenaltyAmount=v;}
    public LocalDate getEffectiveFrom(){return effectiveFrom;} public void setEffectiveFrom(LocalDate v){effectiveFrom=v;}
    public LocalDate getEffectiveTo(){return effectiveTo;} public void setEffectiveTo(LocalDate v){effectiveTo=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public UUID getCreatedBy(){return createdBy;} public void setCreatedBy(UUID v){createdBy=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getSubmittedAt(){return submittedAt;} public void setSubmittedAt(LocalDateTime v){submittedAt=v;}
    public LocalDateTime getWithdrawnAt(){return withdrawnAt;} public void setWithdrawnAt(LocalDateTime v){withdrawnAt=v;}
    public UUID getReviewedBy(){return reviewedBy;} public void setReviewedBy(UUID v){reviewedBy=v;}
    public LocalDateTime getReviewedAt(){return reviewedAt;} public void setReviewedAt(LocalDateTime v){reviewedAt=v;}
    public String getDecisionReason(){return decisionReason;} public void setDecisionReason(String v){decisionReason=v;}
    public UUID getApprovedPolicyId(){return approvedPolicyId;} public void setApprovedPolicyId(UUID v){approvedPolicyId=v;}
}
