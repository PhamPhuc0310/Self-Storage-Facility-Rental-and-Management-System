package com.safebox.self_storage.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** The v2 price is fixed when a reservation is approved; all amounts are whole VND. */
public record ContractPricing(int months, BigDecimal subtotal, BigDecimal vatAmount,
                              BigDecimal total, BigDecimal deposit, BigDecimal remaining) {
    public static ContractPricing of(BigDecimal monthlyPrice, LocalDate start, LocalDate end) {
        if (monthlyPrice == null || monthlyPrice.signum() < 0 || start == null || end == null)
            throw new IllegalArgumentException("Giá hoặc kỳ hạn thuê không hợp lệ.");
        int months = (end.getYear() - start.getYear()) * 12 + end.getMonthValue() - start.getMonthValue();
        if (months <= 0 || !start.plusMonths(months).equals(end))
            throw new IllegalArgumentException("Kỳ hạn thuê phải là số tháng trọn vẹn.");
        BigDecimal subtotal = monthlyPrice.multiply(BigDecimal.valueOf(months)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal vat = subtotal.multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(vat);
        BigDecimal deposit = total.divide(BigDecimal.valueOf(3), 0, RoundingMode.HALF_UP);
        return new ContractPricing(months, subtotal, vat, total, deposit, total.subtract(deposit));
    }
}
