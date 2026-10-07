package com.safebox.self_storage.dto;

/**
 * DTO dữ liệu gửi lên khi khách hàng yêu cầu hủy đặt kho (UC06).
 *
 * @param cancellationReason Lý do hủy yêu cầu (tùy chọn)
 */
public record CancelReservationRequest(
    String cancellationReason
) {}
