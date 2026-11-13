package com.checkout.payment.gateway.app.repository;

import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;

/**
 * Repository read model containing the stored response and the decrypted card number needed by the
 * GET response builder. CVV is never exposed by this record.
 */
public record PaymentRecord(PaymentResponse payment, String cardNumber) {
}
