package com.safebox.self_storage.dto.renewal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO phản hồi kết quả thanh toán gia hạn hợp đồng (UC17 - BR17.5).
 */
public record RenewalPaymentResponse(
        UUID transactionId,
        UUID renewalId,
        UUID contractId,
        String referenceCode,
        BigDecimal amount,
        String paymentMethod,
        String status,
        LocalDateTime transactionDate,
        LocalDate contractNewEndDate,
        String message
) {
}
