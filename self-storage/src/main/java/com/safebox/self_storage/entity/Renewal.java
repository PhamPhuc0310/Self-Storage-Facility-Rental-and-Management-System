package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng RENEWALS trong cơ sở dữ liệu.
 * Quản lý các yêu cầu gia hạn hợp đồng thuê kho (UC17):
 * - BR17.1: Chỉ CUSTOMER sở hữu hợp đồng ACTIVE mới yêu cầu gia hạn.
 * - BR17.2: Ngày bắt đầu kỳ gia hạn nối tiếp ngày kết thúc hiện tại; không ghi đè lịch sử kỳ cũ.
 * - BR17.3: Phải kiểm tra khả dụng/điều kiện cơ sở và các yêu cầu trả kho đang mở trước khi duyệt.
 * - BR17.4: Giá, VAT, ưu đãi (nếu có) được chốt thành snapshot của lần gia hạn.
 * - BR17.5: Chỉ gia hạn hiệu lực sau khi khoản thanh toán được xác nhận; retry không tạo gia hạn trùng.
 */
@Entity
@Table(name = "RENEWALS")
public class Renewal {

    /** Mã định danh duy nhất của yêu cầu gia hạn (Khóa chính) */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "renewal_id", updatable = false, nullable = false)
    private UUID renewalId;

    /** Mã hợp đồng thuê cần gia hạn (Khóa ngoại tham chiếu RENTAL_CONTRACTS) */
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    /** Ngày kết thúc hợp đồng hiện tại trước khi gia hạn */
    @Column(name = "old_end_date", nullable = false)
    private LocalDate oldEndDate;

    /** Ngày kết thúc mới dự kiến sau khi gia hạn */
    @Column(name = "new_end_date", nullable = false)
    private LocalDate newEndDate;

    /** Tổng số tiền cần thanh toán cho kỳ gia hạn (đã bao gồm VAT và trừ ưu đãi) */
    @Column(name = "renewal_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal renewalAmount;

    /** Thời điểm khách hàng gửi yêu cầu gia hạn */
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /** Thời điểm quản lý xét duyệt yêu cầu */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** Quản lý cơ sở xét duyệt yêu cầu (Khóa ngoại tham chiếu USERS) */
    @Column(name = "approved_by")
    private UUID approvedBy;

    /** Hạn chót thanh toán phí gia hạn */
    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;

    /**
     * Trạng thái gia hạn:
     * - PENDING: Chờ quản lý xét duyệt
     * - APPROVED: Quản lý đã duyệt, hóa đơn đã tạo, chờ khách thanh toán
     * - COMPLETED: Đã thanh toán thành công, hợp đồng đã được gia hạn hiệu lực
     * - REJECTED: Quản lý từ chối
     * - CANCELLED: Khách hàng hủy yêu cầu
     * - EXPIRED: Quá hạn thanh toán
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    /** Lý do từ chối nếu bị từ chối */
    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason;

    // =========================================================================
    // Snapshot fields (BR17.4): Giá, VAT, ưu đãi được chốt thành snapshot
    // =========================================================================

    /** Số tháng gia hạn (ví dụ: 1, 3, 6, 12 tháng) */
    @Column(name = "extension_months")
    private Integer extensionMonths;

    /** Đơn giá thuê hàng tháng snapshot tại thời điểm yêu cầu gia hạn */
    @Column(name = "monthly_price", precision = 18, scale = 2)
    private BigDecimal monthlyPrice;

    /** Tiền thuê trước thuế (monthly_price * extension_months) */
    @Column(name = "subtotal", precision = 18, scale = 2)
    private BigDecimal subtotal;

    /** Thuế VAT snapshot (10%) */
    @Column(name = "vat_amount", precision = 18, scale = 2)
    private BigDecimal vatAmount;

    /** Số tiền giảm giá / ưu đãi snapshot */
    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

    /** Mã chính sách giá được áp dụng làm căn cứ snapshot */
    @Column(name = "pricing_id")
    private UUID pricingId;

    public Renewal() {
    }

    public UUID getRenewalId() {
        return renewalId;
    }

    public void setRenewalId(UUID renewalId) {
        this.renewalId = renewalId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public LocalDate getOldEndDate() {
        return oldEndDate;
    }

    public void setOldEndDate(LocalDate oldEndDate) {
        this.oldEndDate = oldEndDate;
    }

    public LocalDate getNewEndDate() {
        return newEndDate;
    }

    public void setNewEndDate(LocalDate newEndDate) {
        this.newEndDate = newEndDate;
    }

    public BigDecimal getRenewalAmount() {
        return renewalAmount;
    }

    public void setRenewalAmount(BigDecimal renewalAmount) {
        this.renewalAmount = renewalAmount;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getPaymentDeadline() {
        return paymentDeadline;
    }

    public void setPaymentDeadline(LocalDateTime paymentDeadline) {
        this.paymentDeadline = paymentDeadline;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Integer getExtensionMonths() {
        return extensionMonths;
    }

    public void setExtensionMonths(Integer extensionMonths) {
        this.extensionMonths = extensionMonths;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public void setMonthlyPrice(BigDecimal monthlyPrice) {
        this.monthlyPrice = monthlyPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public void setVatAmount(BigDecimal vatAmount) {
        this.vatAmount = vatAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public UUID getPricingId() {
        return pricingId;
    }

    public void setPricingId(UUID pricingId) {
        this.pricingId = pricingId;
    }
}
