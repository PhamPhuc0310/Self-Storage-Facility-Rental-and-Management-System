package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.entity.PricingPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrentPricingTest {
    private static PricingPolicy policy(int typeId, String start, String end, int amount) {
        PricingPolicy p = new PricingPolicy();
        p.setPricingId(UUID.randomUUID());
        p.setTypeId(typeId);
        p.setEffectiveFrom(LocalDate.parse(start));
        p.setEffectiveTo(end == null ? null : LocalDate.parse(end));
        p.setMonthlyPrice(BigDecimal.valueOf(amount));
        return p;
    }

    @Test
    void selectsLatestPolicyEffectiveOnDateForMatchingType() {
        var old = policy(1, "2026-01-01", "2026-09-30", 100);
        var current = policy(1, "2026-10-01", null, 200);
        var future = policy(1, "2026-11-01", null, 300);
        var otherType = policy(2, "2026-10-01", null, 400);
        var result = CurrentPricing.forType(List.of(old, current, future, otherType), 1,
            LocalDate.of(2026, 10, 2));
        assertEquals(current, result.orElseThrow());
        assertTrue(CurrentPricing.forType(List.of(old), 1, LocalDate.of(2026, 10, 2)).isEmpty());
    }
}
