package com.safebox.self_storage.dto.manager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OverduePolicyProposalRequest(UUID basePolicyId, Integer overdueGraceDays,
        String penaltyType, BigDecimal dailyPenaltyRate, BigDecimal fixedPenaltyAmount,
        LocalDate effectiveFrom, LocalDate effectiveTo, String reason) {}
