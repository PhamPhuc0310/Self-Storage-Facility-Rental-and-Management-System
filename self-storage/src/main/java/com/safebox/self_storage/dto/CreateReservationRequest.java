package com.safebox.self_storage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO dữ liệu gửi lên khi tạo yêu cầu đặt kho (UC05).
 *
 * @param facilityId Mã UUID của cơ sở lưu trữ (bắt buộc)
 * @param typeId     Mã loại kho cần thuê (bắt buộc)
 * @param startDate  Ngày bắt đầu thuê kho (bắt buộc, không được trong quá khứ)
 * @param months     Số tháng thuê dự kiến (tối thiểu 1 tháng)
 * @param endDate    Ngày kết thúc thuê (tùy chọn, nếu không truyền sẽ tự động tính = startDate + months)
 */
public record CreateReservationRequest(
    @NotNull(message = "Cơ sở không được để trống")
    UUID facilityId,

    @NotNull(message = "Loại kho không được để trống")
    Integer typeId,

    @NotNull(message = "Ngày bắt đầu thuê không được để trống")
    LocalDate startDate,

    @Min(value = 1, message = "Thời gian thuê tối thiểu 1 tháng")
    Integer months,

    LocalDate endDate
) {}
