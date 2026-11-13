package com.checkout.payment.gateway.connector.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class BankSimulatorRequestBuilderTest {

  private final BankSimulatorRequestBuilder builder = new BankSimulatorRequestBuilder();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void buildsGeneratedBankRequestFromPaymentCommand() {
    BankPaymentRequest request = builder.build(new PaymentCommand(
        "2222405343248877", YearMonth.of(2025, 8), "GBP", 100, "123"));

    assertThat(request.getCardNumber()).isEqualTo("2222405343248877");
    assertThat(request.getExpiryDate()).isEqualTo("08/2025");
    assertThat(request.getCurrency()).isEqualTo("GBP");
    assertThat(request.getAmount()).isEqualTo(100);
    assertThat(request.getCvv()).isEqualTo("123");
  }

  @Test
  void padsSingleDigitMonth() {
    BankPaymentRequest request = builder.build(new PaymentCommand(
        "2222405343248877", YearMonth.of(2030, 1), "USD", 500, "999"));

    assertThat(request.getExpiryDate()).isEqualTo("01/2030");
  }

  @Test
  void serializesWithBankSnakeCasePropertyNames() throws Exception {
    BankPaymentRequest request = builder.build(new PaymentCommand(
        "2222405343248877", YearMonth.of(2025, 8), "GBP", 100, "123"));

    String json = objectMapper.writeValueAsString(request);

    assertThat(json)
        .contains("\"card_number\":\"2222405343248877\"")
        .contains("\"expiry_date\":\"08/2025\"")
        .doesNotContain("\"cardNumber\"");
  }
}
