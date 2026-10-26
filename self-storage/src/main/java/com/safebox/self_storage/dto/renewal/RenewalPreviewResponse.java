package com.safebox.self_storage.dto.renewal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO tính toán báo giá / xem trước gia hạn hợp đồng (UC17 - BR17.2, BR17.4).
 */
public record RenewalPreviewResponse(
        UUID contractId,
        String contractNumber,
        LocalDate currentEndDate,
        LocalDate newEndDate,
        int extensionMonths,
        BigDecimal monthlyPrice,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        Integer paymentHours,
        boolean canRenew,
        String message
) {
}
