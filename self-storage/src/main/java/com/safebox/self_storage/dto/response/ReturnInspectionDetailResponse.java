package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO chi tiết biên bản nhận lại kho và kiểm tra tình trạng (UC16 - BR16.5, BR16.8, BR16.9, BR16.10 & UC20 - BR20.1-BR20.5).
 */
public record ReturnInspectionDetailResponse(
        UUID handoverId,
        UUID inspectionId,
        UUID contractId,
        String contractNumber,
        UUID customerId,
        String customerName,
        String customerPhone,
        String customerEmail,
        UUID unitId,
        String unitNumber,
        String floor,
        String zone,
        UUID facilityId,
        String facilityName,
        String facilityAddress,
        LocalDateTime scheduledAt,
        LocalDateTime actualAt,
        String handoverStatus,
        String contractStatus,
        String unitStatus,
        String conditionStatus,
        String damageDescription,
        BigDecimal estimatedRepairCost,
        BigDecimal temperature,
        BigDecimal humidity,
        boolean accessReturned,
        String accessMethod,
        String accessIdentifier,
        boolean customerConfirmed,
        String customerSignature,
        String staffSignature,
        UUID inspectorId,
        String inspectorName,
        BigDecimal depositAmount,
        long unpaidInvoicesCount,
        boolean financialObligationsSettled,
        String financialStatusNotes,
        String note,
        String evidenceUrls
) {
}
