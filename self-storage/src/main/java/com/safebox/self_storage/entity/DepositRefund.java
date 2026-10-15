package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "DEPOSIT_REFUNDS")
public class DepositRefund {
    @Id @Column(name = "refund_id") public UUID id;
    @Column(name = "contract_id", nullable = false) public UUID contractId;
    @Column(name = "customer_id", nullable = false) public UUID customerId;
    @Column(name = "facility_id", nullable = false) public UUID facilityId;
    @Column(name = "payment_reference", nullable = false) public UUID paymentReference;
    @Column(name = "requested_amount", nullable = false) public BigDecimal requestedAmount;
    @Column(name = "approved_amount") public BigDecimal approvedAmount;
    @Column(name = "reason", nullable = false, columnDefinition = "NVARCHAR(MAX)") public String reason;
    @Column(name = "status", nullable = false) public String status;
    @Column(name = "reviewer_id") public UUID reviewerId;
    @Column(name = "review_note", columnDefinition = "NVARCHAR(MAX)") public String reviewNote;
    @Column(name = "refund_transaction_reference") public String refundTransactionReference;
    @Column(name = "created_at", nullable = false) public LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) public LocalDateTime updatedAt;
    @Column(name = "completed_at") public LocalDateTime completedAt;
}
