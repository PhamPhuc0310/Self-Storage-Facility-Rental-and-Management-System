package com.safebox.self_storage.dto;

import com.safebox.self_storage.entity.PaymentTransaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentView(UUID id, String transactionCode, UUID contractId, String paymentType,
                          BigDecimal amount, String paymentMethod, String status,
                          String failureReason, LocalDateTime createdAt) {
    public static PaymentView from(PaymentTransaction p) {
        return new PaymentView(p.transactionId, p.referenceCode, p.contractId, p.paymentType, p.amount,
                p.paymentMethod, p.status, p.failureReason, p.createdAt);
    }
}
