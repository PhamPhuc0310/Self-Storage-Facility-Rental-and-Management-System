package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng INVOICES trong cơ sở dữ liệu.
 * Quản lý các hóa đơn phát sinh từ hợp đồng thuê (tiền cọc, tiền thuê ban đầu, tiền thuê định kỳ, v.v.).
 */
@Entity
@Table(name = "INVOICES")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_id", updatable = false, nullable = false)
    public UUID invoiceId;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    public String invoiceNumber;

    @Column(name = "contract_id", nullable = false)
    public UUID contractId;

    @Column(name = "renewal_id")
    public UUID renewalId;

    @Column(name = "invoice_type", nullable = false, length = 30)
    public String invoiceType;

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    public String description;

    @Column(name = "subtotal", nullable = false, precision = 18, scale = 2)
    public BigDecimal subtotal;

    @Column(name = "penalty_amount", nullable = false, precision = 18, scale = 2)
    public BigDecimal penaltyAmount;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    public BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    public BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    public BigDecimal paidAmount;

    @Column(name = "issued_at", nullable = false)
    public LocalDateTime issuedAt;

    @Column(name = "due_date", nullable = false)
    public LocalDateTime dueDate;

    @Column(name = "status", nullable = false, length = 30)
    public String status;

    public Invoice() {
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public UUID getRenewalId() {
        return renewalId;
    }

    public void setRenewalId(UUID renewalId) {
        this.renewalId = renewalId;
    }

    public String getInvoiceType() {
        return invoiceType;
    }

    public void setInvoiceType(String invoiceType) {
        this.invoiceType = invoiceType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getPenaltyAmount() {
        return penaltyAmount;
    }

    public void setPenaltyAmount(BigDecimal penaltyAmount) {
        this.penaltyAmount = penaltyAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
