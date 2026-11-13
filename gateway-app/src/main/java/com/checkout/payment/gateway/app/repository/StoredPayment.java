package com.checkout.payment.gateway.app.repository;

import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;

/**
 * Persisted payment plus encrypted sensitive card fields. The public response never contains the
 * full card number or CVV.
 */
public record StoredPayment(
    PaymentResponse payment,
    String encryptedCardNumber,
    String encryptedCvv) {
}
