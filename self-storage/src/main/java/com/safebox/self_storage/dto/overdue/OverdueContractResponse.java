package com.safebox.self_storage.dto.overdue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OverdueContractResponse(
        UUID contractId,
        String contractNumber,
        UUID customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        UUID facilityId,
        String facilityName,
        String facilityAddress,
        UUID unitId,
        String unitNumber,
        String unitTypeName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyRentalFee,
        BigDecimal dailyRent,
        int overdueDays,
        int graceDays,
        int billableDays,
        boolean isOverdue,
        boolean hasActiveRenewal,
        boolean hasCompletedReturn,
        BigDecimal totalPenaltyDebt,
        BigDecimal totalPenaltyPaid,
        String status,
        List<PenaltyDetailResponse> penalties
) {}
