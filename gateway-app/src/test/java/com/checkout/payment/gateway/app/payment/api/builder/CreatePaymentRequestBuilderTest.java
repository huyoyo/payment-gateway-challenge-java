package com.checkout.payment.gateway.app.payment.api.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.Source;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.error.PaymentRejectedException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CreatePaymentRequestBuilderTest {

  private final CreatePaymentRequestBuilder builder = new CreatePaymentRequestBuilder();

  @Test
  void buildsConnectorCommandAndNormalizesCardAndCurrency() {
    CreatePaymentRequest request = request(new CardSource("CREDIT_CARD",
        " 2222405343248877 ", 12, 2099, "123"), new Amount(100, " gbp "));

    PaymentCommand command = builder.build(request);

    assertThat(command.cardNumber()).isEqualTo("2222405343248877");
    assertThat(command.expiryDate()).isEqualTo(YearMonth.of(2099, 12));
    assertThat(command.currency()).isEqualTo("GBP");
    assertThat(command.amount()).isEqualTo(100);
    assertThat(command.cvv()).isEqualTo("123");
  }

  @Test
  void rejectsMissingSource() {
    assertRejected(request(null, new Amount(100, "GBP")));
  }

  @Test
  void rejectsUnknownSourceImplementation() {
    assertRejected(request(mock(Source.class), new Amount(100, "GBP")));
  }

  private void assertRejected(CreatePaymentRequest request) {
    assertThatThrownBy(() -> builder.build(request))
        .isInstanceOf(PaymentRejectedException.class)
        .satisfies(exception -> assertThat(
            ((PaymentRejectedException) exception).getErrors())
            .singleElement()
            .satisfies(error -> assertThat(error.field()).isEqualTo("source.type")));
  }

  private static CreatePaymentRequest request(Source source, Amount amount) {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setBankId(BankId.BANK_SIMULATOR);
    request.setSource(source);
    request.setAmount(amount);
    return request;
  }
}
