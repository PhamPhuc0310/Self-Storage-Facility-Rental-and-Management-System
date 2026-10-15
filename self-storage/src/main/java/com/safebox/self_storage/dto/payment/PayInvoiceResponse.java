package com.safebox.self_storage.dto.payment;

/**
 * DTO phản hồi kết quả thanh toán.
 */
public record PayInvoiceResponse(
        boolean success,
        String message,
        InvoiceResponse invoice,
        PaymentTransactionResponse transaction
) {
}
