package com.safebox.self_storage.dto.renewal;

/** A bank transfer needs an independently checked receipt reference; cash does not. */
public record ConfirmRenewalPaymentRequest(String verificationReference) {}
