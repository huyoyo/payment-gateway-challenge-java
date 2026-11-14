package com.checkout.payment.gateway.app.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardDataProtectorTest {

  private final CardDataProtector crypto = new CardDataProtector();

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
  void rejectsNullInputsInvalidBase64AndShortPayloads() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> crypto.encrypt(null, "123"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> crypto.encrypt(id, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> crypto.decrypt(null, "payload"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> crypto.decrypt(id, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> crypto.decrypt(id, "not-base64!"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("base64");
    assertThatThrownBy(() -> crypto.decrypt(id, "c2hvcnQ="))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too short");
  }

  @Test
  void rejectsTamperedCiphertext() {
    UUID id = UUID.randomUUID();
    String encrypted = crypto.encrypt(id, "2222405343248877");
    String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

    assertThatThrownBy(() -> crypto.decrypt(id, tampered))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void usesAnIndependentInMemoryKeyForEachServiceInstance() {
    UUID paymentId = UUID.randomUUID();
    String encrypted = crypto.encrypt(paymentId, "123");
    CardDataProtector anotherInstance = new CardDataProtector();

    assertThatThrownBy(() -> anotherInstance.decrypt(paymentId, encrypted))
        .isInstanceOf(IllegalStateException.class);
  }
}
