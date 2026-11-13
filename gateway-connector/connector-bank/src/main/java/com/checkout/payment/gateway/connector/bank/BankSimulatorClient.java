package com.checkout.payment.gateway.connector.bank;

import com.checkout.payment.gateway.connector.api.BankIntegrationException;
import com.checkout.payment.gateway.connector.api.BankUnavailableException;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentRequest;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentResponse;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Jersey 2.x client for the bank simulator's {@code POST /payments} endpoint.
 *
 * <p>The singleton {@link Client} is supplied by Spring (see
 * {@link BankSimulatorConfiguration}); this class only owns the target and failure mapping.
 */
@Component("gateway.connector.bank.bank-simulator-client")
public class BankSimulatorClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankSimulatorClient.class);

  private final WebTarget paymentsTarget;

  public BankSimulatorClient(Client jerseyClient, BankSimulatorProperties properties) {
    if (!StringUtils.hasText(properties.getBaseUrl())) {
      throw new IllegalStateException("connector.bank.simulator.base-url must be configured");
    }
    this.paymentsTarget = jerseyClient.target(
        properties.getBaseUrl().replaceAll("/+$", "") + "/payments");
  }

  public BankPaymentResponse post(BankPaymentRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Response response;
    try {
      response = paymentsTarget.request(MediaType.APPLICATION_JSON_TYPE)
          .post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE));
    } catch (ProcessingException ex) {
      LOG.warn("Acquiring bank request failed: {}", ex.getClass().getSimpleName());
      throw new BankUnavailableException("Acquiring bank is currently unavailable", ex);
    }

    try (response) {
      int status = response.getStatus();
      if (status / 100 == 2) {
        try {
          BankPaymentResponse body = response.readEntity(BankPaymentResponse.class);
          if (body == null) {
            throw new BankIntegrationException("Acquiring bank returned an empty response");
          }
          LOG.debug("Acquiring bank responded with authorized={}", body.getAuthorized());
          return body;
        } catch (ProcessingException | WebApplicationException ex) {
          throw new BankIntegrationException("Could not process acquiring bank response", ex);
        }
      }

      LOG.warn("Acquiring bank returned HTTP {}", status);
      if (status / 100 == 5) {
        throw new BankUnavailableException("Acquiring bank is currently unavailable", null);
      }
      throw new BankIntegrationException(
          "Acquiring bank rejected the request with HTTP " + status);
    }
  }
}
