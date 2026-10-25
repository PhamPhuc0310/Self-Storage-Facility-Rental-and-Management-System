package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "EARLY_RETURN_SETTLEMENTS")
public class EarlyReturnSettlement {
    @Id @Column(name = "settlement_id") public UUID id;
    @Column(name = "termination_id", nullable = false, unique = true) public UUID terminationId;
    @Column(name = "contract_id", nullable = false) public UUID contractId;
    @Column(name = "policy_id", nullable = false) public UUID policyId;
    @Column(name = "policy_version", nullable = false) public String policyVersion;
    @Column(name = "handover_id", nullable = false) public UUID handoverId;
    @Column(name = "inspection_id", nullable = false) public UUID inspectionId;
    @Column(name = "deposit_contract_amount", nullable = false) public BigDecimal depositContractAmount;
    @Column(name = "deposit_collected", nullable = false) public BigDecimal depositCollected;
    @Column(name = "prior_deposit_refunds", nullable = false) public BigDecimal priorDepositRefunds;
    @Column(name = "rental_collected", nullable = false) public BigDecimal rentalCollected;
    @Column(name = "unused_rent_refund", nullable = false) public BigDecimal unusedRentRefund;
    @Column(name = "outstanding_receivables", nullable = false) public BigDecimal outstandingReceivables;
    @Column(name = "inspection_deduction", nullable = false) public BigDecimal inspectionDeduction;
    @Column(name = "deduction_reason") public String deductionReason;
    @Column(name = "deposit_refund_amount", nullable = false) public BigDecimal depositRefundAmount;
    @Column(name = "remaining_payable", nullable = false) public BigDecimal remainingPayable;
    @Column(name = "status", nullable = false) public String status;
    @Column(name = "calculated_at", nullable = false) public LocalDateTime calculatedAt;
    @Column(name = "approved_at", nullable = false) public LocalDateTime approvedAt;
    @Column(name = "approved_by", nullable = false) public UUID approvedBy;
    @Column(name = "refund_id") public UUID refundId;
    @Column(name = "refund_transaction_id") public UUID refundTransactionId;
}
