package com.safebox.self_storage.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoTransferDetailsController {
    @Value("${app.payment.demo-transfer.enabled:false}") private boolean enabled;
    @Value("${app.payment.demo-transfer.bank:}") private String bank;
    @Value("${app.payment.demo-transfer.account-holder:}") private String accountHolder;
    @Value("${app.payment.demo-transfer.account-number:}") private String accountNumber;
    @Value("${app.payment.demo-transfer.qr-path:}") private String qrPath;

    public record Details(boolean enabled, String bank, String accountHolder, String accountNumber, String qrPath) {}

    @GetMapping("/api/payment/demo-transfer-details")
    public Details details() {
        boolean available = enabled && !accountNumber.isBlank() && !qrPath.isBlank();
        return available ? new Details(true, bank, accountHolder, accountNumber, qrPath)
                : new Details(false, "", "", "", "");
    }
}
