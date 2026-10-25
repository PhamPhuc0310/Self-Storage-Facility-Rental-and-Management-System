package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO kết quả hoàn tất kiểm tra trả kho và đóng hợp đồng (UC20 - BR20.1-BR20.5).
 */
public record ContractCompletionResponse(
        UUID contractId,
        String contractNumber,
        String contractStatus,
        LocalDate actualEndDate,
        UUID unitId,
        String unitNumber,
        String unitStatus,
        UUID facilityId,
        String facilityName,
        UUID customerId,
        String customerName,
        UUID completedBy,
        String completedByName,
        LocalDateTime completedAt,
        String inspectionSummary,
        BigDecimal depositAmount,
        String depositStatus,
        String settlementSummary,
        String message
) {
}
