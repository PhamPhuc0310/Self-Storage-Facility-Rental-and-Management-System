package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng RENTAL_CONTRACTS trong cơ sở dữ liệu.
 * Quản lý thông tin hợp đồng thuê kho tự quản giữa khách hàng và SafeBox.
 */
@Entity
@Table(name = "RENTAL_CONTRACTS")
public class RentalContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "contract_id", updatable = false, nullable = false)
    public UUID contractId;

    @Column(name = "contract_number", nullable = false, unique = true, length = 50)
    public String contractNumber;

    @Column(name = "reservation_id", nullable = false, unique = true)
    public UUID reservationId;

    @Column(name = "customer_id", nullable = false)
    public UUID customerId;

    @Column(name = "unit_id", nullable = false)
    public UUID unitId;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    public LocalDate endDate;

    @Column(name = "actual_start_date")
    public LocalDate actualStartDate;

    @Column(name = "actual_end_date")
    public LocalDate actualEndDate;

    @Column(name = "monthly_rental_fee", nullable = false, precision = 18, scale = 2)
    public BigDecimal monthlyRentalFee;

    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 2)
    public BigDecimal depositAmount;

    @Column(name = "billing_cycle", nullable = false, length = 20)
    public String billingCycle;

    @Column(name = "terms_version", nullable = false, length = 30)
    public String termsVersion;

    @Column(name = "customer_signed_at")
    public LocalDateTime customerSignedAt;

    @Column(name = "customer_signature", columnDefinition = "NVARCHAR(MAX)")
    public String customerSignature;

    @Column(name = "approved_by")
    public UUID approvedBy;

    @Column(name = "approved_at")
    public LocalDateTime approvedAt;

    @Column(name = "company_signature", columnDefinition = "NVARCHAR(MAX)")
    public String companySignature;

    @Column(name = "status", nullable = false, length = 30)
    public String status;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    public RentalContract() {
    }

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public void setUnitId(UUID unitId) {
        this.unitId = unitId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDate getActualStartDate() {
        return actualStartDate;
    }

    public void setActualStartDate(LocalDate actualStartDate) {
        this.actualStartDate = actualStartDate;
    }

    public LocalDate getActualEndDate() {
        return actualEndDate;
    }

    public void setActualEndDate(LocalDate actualEndDate) {
        this.actualEndDate = actualEndDate;
    }

    public BigDecimal getMonthlyRentalFee() {
        return monthlyRentalFee;
    }

    public void setMonthlyRentalFee(BigDecimal monthlyRentalFee) {
        this.monthlyRentalFee = monthlyRentalFee;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public void setTermsVersion(String termsVersion) {
        this.termsVersion = termsVersion;
    }

    public LocalDateTime getCustomerSignedAt() {
        return customerSignedAt;
    }

    public void setCustomerSignedAt(LocalDateTime customerSignedAt) {
        this.customerSignedAt = customerSignedAt;
    }

    public String getCustomerSignature() {
        return customerSignature;
    }

    public void setCustomerSignature(String customerSignature) {
        this.customerSignature = customerSignature;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getCompanySignature() {
        return companySignature;
    }

    public void setCompanySignature(String companySignature) {
        this.companySignature = companySignature;
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
}
