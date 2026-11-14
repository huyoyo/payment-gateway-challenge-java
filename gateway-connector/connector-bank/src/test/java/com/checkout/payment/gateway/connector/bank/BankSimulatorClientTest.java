package com.checkout.payment.gateway.connector.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.connector.api.BankIntegrationException;
import com.checkout.payment.gateway.connector.api.BankUnavailableException;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentRequest;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentResponse;
import java.time.Duration;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankSimulatorClientTest {

  @Mock
  private Client jerseyClient;
  @Mock
  private WebTarget paymentsTarget;
  @Mock
  private Invocation.Builder requestBuilder;
  @Mock
  private Response response;

  private BankSimulatorClient client;

  @BeforeEach
  void setUp() {
    BankSimulatorProperties properties = new BankSimulatorProperties();
    properties.setBankId("bank-simulator");
    properties.setBaseUrl("http://bank.test/");
    properties.setConnectTimeout(Duration.ofSeconds(1));
    properties.setReadTimeout(Duration.ofSeconds(1));

    lenient().when(jerseyClient.target("http://bank.test/payments")).thenReturn(paymentsTarget);
    lenient().when(paymentsTarget.request(MediaType.APPLICATION_JSON_TYPE)).thenReturn(requestBuilder);
    client = new BankSimulatorClient(jerseyClient, properties);
  }

  @Test
  void postsGeneratedRequestAndReturnsGeneratedResponse() {
    BankPaymentRequest request = request();
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("auth-123");
    when(requestBuilder.post(any(Entity.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(200);
    when(response.readEntity(BankPaymentResponse.class)).thenReturn(bankResponse);

    BankPaymentResponse result = client.post(request);

    assertThat(result.getAuthorized()).isTrue();
    assertThat(result.getAuthorizationCode()).isEqualTo("auth-123");
    ArgumentCaptor<Entity<?>> entityCaptor = ArgumentCaptor.forClass(Entity.class);
    verify(requestBuilder).post(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getEntity()).isSameAs(request);
    assertThat(entityCaptor.getValue().getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
  }

  @Test
  void mapsServerErrorToUnavailable() {
    when(requestBuilder.post(any(Entity.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(503);

    assertThatThrownBy(() -> client.post(request()))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void mapsClientErrorToIntegrationFailure() {
    when(requestBuilder.post(any(Entity.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(400);

    assertThatThrownBy(() -> client.post(request()))
        .isInstanceOf(BankIntegrationException.class);
  }

  @Test
  void mapsConnectionFailureToUnavailable() {
    when(requestBuilder.post(any(Entity.class))).thenThrow(new ProcessingException("offline"));

    assertThatThrownBy(() -> client.post(request()))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void mapsEmptyBodyToIntegrationFailure() {
    when(requestBuilder.post(any(Entity.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(200);
    when(response.readEntity(BankPaymentResponse.class)).thenReturn(null);

    assertThatThrownBy(() -> client.post(request()))
        .isInstanceOf(BankIntegrationException.class);
  }

  @Test
  void mapsUnreadableBodyToIntegrationFailure() {
    when(requestBuilder.post(any(Entity.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(200);
    when(response.readEntity(BankPaymentResponse.class))
        .thenThrow(new ProcessingException("invalid response"));

    assertThatThrownBy(() -> client.post(request()))
        .isInstanceOf(BankIntegrationException.class);
  }

  private static BankPaymentRequest request() {
    BankPaymentRequest request = new BankPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryDate("08/2025");
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  @Test
  void rejectsBlankBaseUrl() {
    BankSimulatorProperties properties = new BankSimulatorProperties();
    properties.setBaseUrl(" ");

    assertThatThrownBy(() -> new BankSimulatorClient(jerseyClient, properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("base-url");
  }
}
