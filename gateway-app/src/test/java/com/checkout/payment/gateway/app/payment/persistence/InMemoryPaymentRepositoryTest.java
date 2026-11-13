package com.checkout.payment.gateway.app.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryPaymentRepositoryTest {

  private final CardDataProtector protector = new CardDataProtector();
  private final PaymentPersistenceMapper mapper = new PaymentPersistenceMapper(protector);
  private final InMemoryPaymentRepository repository = new InMemoryPaymentRepository(mapper);

  @Test
  void decryptsCardNumberOnReadAndReturnsOnlyMaskedValue() {
    UUID id = UUID.randomUUID();
    PaymentResponse payment = new PaymentResponse();
    payment.setId(id);
    PaymentSource source = new PaymentSource();
    source.setType(PaymentSource.TypeEnum.CREDIT_CARD);
    payment.setSource(source);

    StoredPayment stored = mapper.toStored(payment,
        new com.checkout.payment.gateway.connector.api.PaymentCommand(
            "2222405343248877", java.time.YearMonth.of(2030, 6), "GBP", 100, "123"));
    repository.save(stored);

    PaymentRecord loaded = repository.findById(id).orElseThrow();
    assertThat(loaded.maskedCardNumber()).isEqualTo("**** **** **** 8877");
    assertThat(loaded.cardNumberLastFour()).isEqualTo("8877");
    assertThat(loaded.payment().getSource().getNumber()).isNull();
  }
}
