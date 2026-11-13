package com.checkout.payment.gateway.app.payment.persistence;

import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;

/**
 * Persisted payment plus the encrypted card number. CVV is deliberately not part of this model.
 */
public record StoredPayment(PaymentResponse payment, String encryptedPan) {
}
