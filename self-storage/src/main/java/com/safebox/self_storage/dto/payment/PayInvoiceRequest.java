package com.safebox.self_storage.dto.payment;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO yêu cầu thanh toán hóa đơn.
 */
public record PayInvoiceRequest(
        @NotBlank(message = "Vui lòng chọn phương thức thanh toán")
        String paymentMethod,

        String referenceCode,

        String notes
) {
}
