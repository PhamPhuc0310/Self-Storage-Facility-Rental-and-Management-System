package com.safebox.self_storage.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContractPricingTest {
    @Test void threeMonthPriceIncludesVatAndDepositIsPartOfTotal() {
        ContractPricing p = ContractPricing.of(new BigDecimal("900000"),
                LocalDate.of(2026,11,1), LocalDate.of(2027,2,1));
        assertEquals(3, p.months());
        assertEquals(new BigDecimal("2700000"), p.subtotal());
        assertEquals(new BigDecimal("270000"), p.vatAmount());
        assertEquals(new BigDecimal("2970000"), p.total());
        assertEquals(new BigDecimal("990000"), p.deposit());
        assertEquals(new BigDecimal("1980000"), p.remaining());
        assertEquals(0, p.deposit().add(p.remaining()).compareTo(p.total()));
    }
    @Test void partialMonthIsRejectedInsteadOfSilentlyUndercharging() {
        assertThrows(IllegalArgumentException.class, () -> ContractPricing.of(new BigDecimal("900000"),
                LocalDate.of(2026,11,1), LocalDate.of(2027,2,2)));
    }
    @Test void vatAndDepositRoundToWholeVndWithoutChangingTheTotal() {
        ContractPricing p = ContractPricing.of(new BigDecimal("100001"),
                LocalDate.of(2026,11,1), LocalDate.of(2026,12,1));
        assertEquals(new BigDecimal("100001"), p.subtotal());
        assertEquals(new BigDecimal("10000"), p.vatAmount());
        assertEquals(new BigDecimal("110001"), p.total());
        assertEquals(new BigDecimal("36667"), p.deposit());
        assertEquals(new BigDecimal("73334"), p.remaining());
        assertEquals(p.total(), p.deposit().add(p.remaining()));
    }
}
