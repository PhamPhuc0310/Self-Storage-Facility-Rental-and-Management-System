package com.safebox.self_storage.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO phản hồi chi tiết hợp đồng thuê kho (UC15 - BR15.5):
 * - Thông tin hợp đồng tổng quan
 * - Các khoản đã thu / còn thiếu
 * - Lịch nhận/trả và biên bản bàn giao nếu đã phát sinh
 * - Lịch sử thanh toán và hóa đơn phát sinh
 */
public record RentalContractDetailResponse(
    RentalContractListItemResponse contract,
    String billingCycle,
    String termsVersion,
    LocalDateTime customerSignedAt,
    LocalDateTime approvedAt,
    String customerName,
    String customerEmail,
    String customerPhone,
    List<InvoiceSummaryResponse> invoices,
    List<PaymentTransactionSummaryResponse> payments,
    List<HandoverRecordSummaryResponse> handoverRecords,
    List<InspectionReportSummaryResponse> inspectionReports
) {
}
