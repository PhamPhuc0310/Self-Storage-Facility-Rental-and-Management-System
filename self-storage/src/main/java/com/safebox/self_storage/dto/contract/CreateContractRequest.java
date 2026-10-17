package com.safebox.self_storage.dto.contract;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO yêu cầu gán kho vật lý và lập hợp đồng thuê (UC09).
 */
public record CreateContractRequest(
        @NotNull(message = "Mã kho vật lý không được để trống")
        UUID unitId,
        String termsVersion,
        String note
) {
}
