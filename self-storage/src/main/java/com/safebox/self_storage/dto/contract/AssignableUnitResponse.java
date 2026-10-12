package com.safebox.self_storage.dto.contract;

import java.util.UUID;

/**
 * DTO trả về danh sách ô kho vật lý khả dụng để Quản lý cơ sở lựa chọn gán cho đơn đặt kho (BR09.9).
 */
public record AssignableUnitResponse(
        UUID unitId,
        String unitNumber,
        Integer typeId,
        String floor,
        String zone,
        String status
) {
}
