package com.checkout.payment.gateway.connector.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.connector.api.BankIntegrationException;
import com.checkout.payment.gateway.connector.api.BankUnavailableException;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentRequest;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentResponse;
import com.sun.net.httpserver.HttpServer;
import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BankSimulatorClientTest {

  private HttpServer server;
  private Client jerseyClient;
  private BankSimulatorClient client;
  private final AtomicReference<String> receivedBody = new AtomicReference<>();

  private volatile int responseStatus = 200;
  private volatile String responseBody = "";

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/payments", exchange -> {
      receivedBody.set(new String(exchange.getRequestBody().readAllBytes(),
          StandardCharsets.UTF_8));
      byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(responseStatus, bytes.length == 0 ? -1 : bytes.length);
      if (bytes.length > 0) {
        exchange.getResponseBody().write(bytes);
      }
      exchange.close();
    });
    server.start();

    BankSimulatorProperties properties = new BankSimulatorProperties();
    properties.setBankId("bank-simulator");
    properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
    properties.setConnectTimeout(Duration.ofSeconds(1));
    properties.setReadTimeout(Duration.ofSeconds(1));

    jerseyClient = new BankSimulatorConfiguration().bankJerseyClient(properties);
    client = new BankSimulatorClient(jerseyClient, properties);
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
    if (jerseyClient != null) {
      jerseyClient.close();
    }
  }

  @Test
  void postsGeneratedRequestAndReturnsGeneratedResponse() {
    responseBody = "{\"authorized\":true,\"authorization_code\":\"auth-123\"}";

    BankPaymentResponse response = client.post(request());

    assertThat(response.getAuthorized()).isTrue();
    assertThat(response.getAuthorizationCode()).isEqualTo("auth-123");
    assertThat(receivedBody.get())
        .contains("\"card_number\":\"2222405343248877\"")
        .contains("\"expiry_date\":\"08/2025\"");
  }

  @Test
  void mapsServerErrorToUnavailable() {
    responseStatus = 503;

    assertThatThrownBy(() -> client.post(request()))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void mapsClientErrorToIntegrationFailure() {
    responseStatus = 400;

    assertThatThrownBy(() -> client.post(request()))
        .isInstanceOf(BankIntegrationException.class);
  }

  @Test
  void mapsConnectionFailureToUnavailable() {
    server.stop(0);

    assertThatThrownBy(() -> client.post(request()))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void mapsEmptyBodyToIntegrationFailure() {
    responseBody = "";

    assertThatThrownBy(() -> client.post(request()))
        .isInstanceOf(BankIntegrationException.class);
  }

  @Test
  void mapsMalformedBodyToIntegrationFailure() {
    responseBody = "not-json";

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
}
