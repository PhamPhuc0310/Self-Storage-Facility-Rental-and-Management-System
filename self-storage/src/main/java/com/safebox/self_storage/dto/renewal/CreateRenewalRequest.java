package com.safebox.self_storage.dto.renewal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO yêu cầu gia hạn hợp đồng thuê kho (UC17 - BR17.1, BR17.2).
 */
public class CreateRenewalRequest {

    /**
     * Số tháng cần gia hạn (tối thiểu 1 tháng, tối đa 60 tháng).
     */
    @NotNull(message = "Số tháng gia hạn không được để trống")
    @Min(value = 1, message = "Kỳ hạn gia hạn tối thiểu là 1 tháng")
    @Max(value = 60, message = "Kỳ hạn gia hạn tối đa là 60 tháng")
    private Integer months;

    /**
     * Ghi chú bổ sung từ khách hàng (nếu có).
     */
    private String note;

    /**
     * Số tiền ưu đãi / voucher nếu có áp dụng.
     */
    private BigDecimal discountAmount;

    public CreateRenewalRequest() {
    }

    public CreateRenewalRequest(Integer months) {
        this.months = months;
    }

    public CreateRenewalRequest(Integer months, String note, BigDecimal discountAmount) {
        this.months = months;
        this.note = note;
        this.discountAmount = discountAmount;
    }

    public Integer getMonths() {
        return months;
    }

    public void setMonths(Integer months) {
        this.months = months;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }
}
