package com.checkout.payment.gateway.app.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentPersistenceMapperTest {

  private final CardDataProtector protector = new CardDataProtector();
  private final PaymentPersistenceMapper mapper = new PaymentPersistenceMapper(protector);

  @Test
  void encryptsPanAndDoesNotPersistCvv() {
    UUID id = UUID.randomUUID();
    PaymentResponse payment = new PaymentResponse();
    payment.setId(id);
    PaymentCommand command = new PaymentCommand(
        "2222405343248877", YearMonth.of(2030, 6), "GBP", 100, "123");

    StoredPayment stored = mapper.toStored(payment, command);

    assertThat(stored.encryptedPan()).doesNotContain("2222405343248877");
    assertThat(stored.payment()).isSameAs(payment);
    PaymentRecord record = mapper.toRecord(stored);
    assertThat(record.maskedCardNumber()).isEqualTo("**** **** **** 8877");
    assertThat(record.lastFour()).isEqualTo("8877");
  }
}
