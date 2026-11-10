package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

/** Versioned approved policy; existing rows retain deposit-only behavior (rate zero). */
@Entity
@Table(name = "EARLY_RETURN_POLICIES")
public class EarlyReturnPolicy {
    @Id @Column(name = "policy_id") public UUID id;
    @Column(name = "facility_id", nullable = false) public UUID facilityId;
    @Column(name = "version", nullable = false) public String version;
    @Column(name = "effective_from", nullable = false) public LocalDate effectiveFrom;
    @Column(name = "effective_to") public LocalDate effectiveTo;
    @Column(name = "deposit_refundable", nullable = false) public boolean depositRefundable;
    @Column(name = "inspection_deduction_allowed", nullable = false) public boolean inspectionDeductionAllowed;
    @Column(name = "status", nullable = false) public String status;
    @Column(name = "rental_refund_percent", nullable = false) public BigDecimal rentalRefundPercent = BigDecimal.ZERO;
}
