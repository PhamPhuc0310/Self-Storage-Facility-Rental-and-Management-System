package com.safebox.self_storage.dto.renewal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO Quản lý xét duyệt yêu cầu gia hạn hợp đồng (UC17 - BR17.3).
 */
public class ReviewRenewalRequest {

    /**
     * Hành động xét duyệt: APPROVE hoặc REJECT.
     */
    @NotBlank(message = "Hành động xét duyệt không được để trống")
    @Pattern(regexp = "(?i)APPROVE|REJECT", message = "Hành động phải là APPROVE hoặc REJECT")
    private String action;

    /**
     * Lý do từ chối (bắt buộc khi action là REJECT).
     */
    private String reason;

    /**
     * Tùy chỉnh số giờ thanh toán (nếu quản lý muốn gia hạn hạn chót thanh toán so với chính sách mặc định).
     */
    private Integer customPaymentHours;

    public ReviewRenewalRequest() {
    }

    public ReviewRenewalRequest(String action, String reason) {
        this.action = action;
        this.reason = reason;
    }

    public ReviewRenewalRequest(String action, String reason, Integer customPaymentHours) {
        this.action = action;
        this.reason = reason;
        this.customPaymentHours = customPaymentHours;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getCustomPaymentHours() {
        return customPaymentHours;
    }

    public void setCustomPaymentHours(Integer customPaymentHours) {
        this.customPaymentHours = customPaymentHours;
    }
}
