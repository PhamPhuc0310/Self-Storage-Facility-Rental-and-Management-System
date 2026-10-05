package com.safebox.self_storage.dto;

/**
 * DTO dữ liệu gửi lên khi ban quản lý từ chối yêu cầu đặt kho (UC08).
 *
 * @param rejectionReason Lý do từ chối yêu cầu (tùy chọn)
 */
public record RejectReservationRequest(
    String rejectionReason
) {}
