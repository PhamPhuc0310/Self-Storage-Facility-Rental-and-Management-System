package com.safebox.self_storage.dto.manager;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceScheduleRequest(Integer typeId, BigDecimal monthlyPrice,
                                   LocalDate effectiveFrom, LocalDate effectiveTo) {}
