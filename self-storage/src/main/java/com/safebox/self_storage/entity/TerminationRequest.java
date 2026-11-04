package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "TERMINATION_REQUESTS")
public class TerminationRequest {
    @Id @Column(name = "termination_id") public UUID id;
    @Column(name = "contract_id", nullable = false) public UUID contractId;
    @Column(name = "termination_type", nullable = false) public String type;
    @Column(name = "requested_by", nullable = false) public UUID requestedBy;
    @Column(name = "requested_at", nullable = false) public LocalDateTime requestedAt;
    @Column(name = "scheduled_return_at") public LocalDateTime scheduledReturnAt;
    @Column(name = "reason") public String reason;
    @Column(name = "deposit_amount", nullable = false) public BigDecimal depositAmount;
    @Column(name = "deduction_amount", nullable = false) public BigDecimal deductionAmount;
    @Column(name = "refund_amount", nullable = false) public BigDecimal refundAmount;
    @Column(name = "status", nullable = false) public String status;
    @Column(name = "approved_by") public UUID approvedBy;
    @Column(name = "completed_at") public LocalDateTime completedAt;
}
