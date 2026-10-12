package com.safebox.self_storage.dto.contract;

import jakarta.validation.constraints.NotNull;

/**
 * DTO yêu cầu xác nhận hợp đồng thuê (UC10).
 */
public record ConfirmContractRequest(
        @NotNull(message = "Vui lòng xác nhận đồng ý với các điều khoản thuê kho")
        Boolean agreedToTerms,
        String signature
) {
}
