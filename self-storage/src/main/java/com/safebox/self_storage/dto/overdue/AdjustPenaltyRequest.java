package com.safebox.self_storage.dto.overdue;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO yêu cầu điều chỉnh công nợ phí phạt (BR18.5).
 * Không tự động xóa nợ; phải có lý do điều chỉnh rõ ràng và lưu vết kiểm toán.
 */
public class AdjustPenaltyRequest {

    /**
     * Số tiền sau điều chỉnh (VND). Ví dụ: miễn giảm 100% thì = 0, giảm một phần thì = số tiền mới.
     */
    @NotNull(message = "Số tiền sau điều chỉnh không được để trống")
    @DecimalMin(value = "0", message = "Số tiền sau điều chỉnh không được âm")
    private BigDecimal adjustedAmount;

    /**
     * Loại điều chỉnh: WAIVER (miễn toàn bộ), PARTIAL_DISCOUNT (giảm một phần), CORRECTION (hiệu chỉnh).
     */
    @NotBlank(message = "Loại điều chỉnh không được để trống")
    private String adjustmentType;

    /**
     * Lý do điều chỉnh (bắt buộc theo BR18.5).
     */
    @NotBlank(message = "Lý do điều chỉnh không được để trống")
    private String reason;

    /**
     * Ghi chú bổ sung (tùy chọn).
     */
    private String note;

    public AdjustPenaltyRequest() {
    }

    public AdjustPenaltyRequest(BigDecimal adjustedAmount, String adjustmentType, String reason, String note) {
        this.adjustedAmount = adjustedAmount;
        this.adjustmentType = adjustmentType;
        this.reason = reason;
        this.note = note;
    }

    public BigDecimal getAdjustedAmount() {
        return adjustedAmount;
    }

    public void setAdjustedAmount(BigDecimal adjustedAmount) {
        this.adjustedAmount = adjustedAmount;
    }

    public String getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(String adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
