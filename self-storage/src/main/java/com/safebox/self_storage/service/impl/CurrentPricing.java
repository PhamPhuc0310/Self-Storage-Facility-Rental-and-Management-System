package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.entity.PricingPolicy;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class CurrentPricing {
    private CurrentPricing() {}

    static Optional<PricingPolicy> forType(List<PricingPolicy> policies, Integer typeId, LocalDate date) {
        return policies.stream()
            .filter(p -> typeId.equals(p.getTypeId()))
            .filter(p -> p.getEffectiveFrom() != null && !p.getEffectiveFrom().isAfter(date))
            .filter(p -> p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(date))
            .max(Comparator.comparing(PricingPolicy::getEffectiveFrom)
                .thenComparing(p -> p.getPricingId().toString()));
    }
}
