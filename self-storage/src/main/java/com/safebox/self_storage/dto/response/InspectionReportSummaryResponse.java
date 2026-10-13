package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InspectionReportSummaryResponse(
    UUID inspectionId,
    String inspectionType,
    LocalDateTime inspectionDate,
    String conditionStatus,
    BigDecimal temperature,
    BigDecimal humidity,
    String damageDescription,
    BigDecimal estimatedRepairCost,
    boolean customerConfirmed,
    String note
) {
}
