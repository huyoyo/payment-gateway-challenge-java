package com.checkout.payment.gateway.app.payment.persistence;

import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;

/**
 * Read model for GET responses. Only masked card data leaves the persistence boundary.
 */
public record PaymentRecord(
    PaymentResponse payment,
    String maskedCardNumber,
    String cardNumberLastFour) {
}
