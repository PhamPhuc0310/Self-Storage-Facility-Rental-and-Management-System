package com.safebox.self_storage.dto.overdue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PenaltyDetailResponse(
        UUID penaltyId,
        UUID contractId,
        String contractNumber,
        UUID invoiceId,
        String invoiceNumber,
        LocalDate periodDate,
        int overdueDays,
        int billableDays,
        BigDecimal dailyRate,
        BigDecimal penaltyRate,
        BigDecimal penaltyAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String calculationExplanation,
        String idempotencyKey,
        String status,
        LocalDateTime createdAt,
        List<AdjustmentDetailResponse> adjustments
) {}
