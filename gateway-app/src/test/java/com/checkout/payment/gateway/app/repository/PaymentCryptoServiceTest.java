package com.checkout.payment.gateway.app.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCryptoServiceTest {

  private final PaymentCryptoService crypto = new PaymentCryptoService();

  @Test
  void encryptsAndDecryptsCardDataForTheSamePaymentId() {
    UUID paymentId = UUID.randomUUID();

    String encrypted = crypto.encrypt(paymentId, "2222405343248877");

    assertThat(encrypted).doesNotContain("2222405343248877");
    assertThat(crypto.decrypt(paymentId, encrypted)).isEqualTo("2222405343248877");
  }

  @Test
  void ciphertextIsBoundToThePaymentId() {
    UUID paymentId = UUID.randomUUID();
    String encrypted = crypto.encrypt(paymentId, "123");

    assertThatThrownBy(() -> crypto.decrypt(UUID.randomUUID(), encrypted))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void usesAnIndependentInMemoryKeyForEachServiceInstance() {
    UUID paymentId = UUID.randomUUID();
    String encrypted = crypto.encrypt(paymentId, "123");
    PaymentCryptoService anotherInstance = new PaymentCryptoService();

    assertThatThrownBy(() -> anotherInstance.decrypt(paymentId, encrypted))
        .isInstanceOf(IllegalStateException.class);
  }
}
