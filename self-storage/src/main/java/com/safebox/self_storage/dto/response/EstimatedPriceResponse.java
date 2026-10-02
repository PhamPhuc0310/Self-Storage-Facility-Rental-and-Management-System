package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;

public record EstimatedPriceResponse(
    BigDecimal monthlyPrice,
    Integer months,
    BigDecimal estimatedTotal
) {}
