package com.safebox.self_storage.dto.overdue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdjustmentDetailResponse(
        UUID adjustmentId,
        UUID penaltyId,
        UUID contractId,
        BigDecimal originalAmount,
        BigDecimal adjustedAmount,
        BigDecimal discountAmount,
        String adjustmentType,
        String reason,
        UUID adjustedBy,
        String adjustedByName,
        LocalDateTime adjustedAt,
        String note
) {}
