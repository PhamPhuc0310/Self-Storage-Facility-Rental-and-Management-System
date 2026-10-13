package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng RENTAL_CONTRACTS trong cơ sở dữ liệu.
 * Quản lý thông tin hợp đồng thuê kho tự quản giữa khách hàng và SafeBox:
 * - UC09: Gán kho vật lý và lập hợp đồng thuê (FACILITY_MANAGER)
 * - UC10: Xem và xác nhận hợp đồng thuê (CUSTOMER)
 * - UC15: Xem và quản lý các kho đang thuê
 */
@Entity
@Table(name = "RENTAL_CONTRACTS")
public class RentalContract {

    /** Mã định danh duy nhất của hợp đồng (Khóa chính) */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "contract_id", updatable = false, nullable = false)
    private UUID contractId;

    /** Mã số hợp đồng duy nhất hiển thị cho người dùng (ví dụ: HD-20261010-ABCD) */
    @Column(name = "contract_number", nullable = false, unique = true, length = 50)
    private String contractNumber;

    /** Mã yêu cầu đặt kho liên kết (Mỗi yêu cầu chỉ có 1 hợp đồng) */
    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;

    /** Mã khách hàng thuê kho (Khóa ngoại tham chiếu USERS) */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Mã kho vật lý được gán cụ thể (Khóa ngoại tham chiếu STORAGE_UNITS) */
    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    /** Ngày bắt đầu thuê hợp đồng */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Ngày kết thúc thuê hợp đồng */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Ngày bắt đầu thực tế khi bàn giao (UC14) */
    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    /** Ngày kết thúc thực tế khi trả kho */
    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    /** Giá thuê hàng tháng đã chốt tại thời điểm lập hợp đồng */
    @Column(name = "monthly_rental_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyRentalFee;

    /** Số tiền đặt cọc cần đóng */
    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositAmount;

    /** Kỳ thanh toán (mặc định MONTHLY) */
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private String billingCycle;

    /** Phiên bản điều khoản hợp đồng áp dụng */
    @Column(name = "terms_version", nullable = false, length = 30)
    private String termsVersion;

    /** Thời điểm khách hàng ký xác nhận điện tử */
    @Column(name = "customer_signed_at")
    private LocalDateTime customerSignedAt;

    /** Chữ ký hoặc bằng chứng xác nhận điện tử của khách hàng */
    @Column(name = "customer_signature", columnDefinition = "NVARCHAR(MAX)")
    private String customerSignature;

    /** Quản lý cơ sở lập và phê duyệt hợp đồng */
    @Column(name = "approved_by")
    private UUID approvedBy;

    /** Thời điểm quản lý lập và phê duyệt hợp đồng */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Chữ ký đại diện công ty / quản lý cơ sở */
    @Column(name = "company_signature", columnDefinition = "NVARCHAR(MAX)")
    private String companySignature;

    /**
     * Trạng thái hợp đồng:
     * - PENDING_CONFIRMATION: Chờ khách hàng xác nhận (Sau UC09)
     * - PENDING_HANDOVER: Chờ bàn giao (Sau khi khách ký UC10)
     * - ACTIVE: Đang hiệu lực (Sau khi bàn giao kho UC14)
     * - CANCELLED: Đã hủy
     * - TERMINATED: Đã thanh lý / kết thúc
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    /** Thời điểm tạo bản ghi hợp đồng */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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
