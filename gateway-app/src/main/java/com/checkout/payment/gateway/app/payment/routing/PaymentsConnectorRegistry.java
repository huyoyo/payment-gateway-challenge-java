package com.checkout.payment.gateway.app.payment.routing;

import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.connector.api.IPaymentProcessor;
import com.checkout.payment.gateway.connector.api.error.UnsupportedBankException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Resolves the bank connector for a request from its bank id.
 *
 * <p>All {@link IPaymentProcessor} beans are discovered through constructor injection.
 * Invalid or duplicate ids fail fast at startup, so routing cannot silently pick an arbitrary
 * connector.
 */
@Component("gateway.app.connector.payments-connector-registry")
public class PaymentsConnectorRegistry {

  private final Map<BankId, IPaymentProcessor> connectors;

  public PaymentsConnectorRegistry(List<IPaymentProcessor> connectors) {
    if (connectors == null || connectors.isEmpty()) {
      throw new IllegalStateException("No IPaymentProcessor connectors are configured");
    }
    Map<BankId, IPaymentProcessor> byBankId = new TreeMap<>();
    for (IPaymentProcessor connector : connectors) {
      String configuredBankId = connector.getBankId();
      if (configuredBankId == null || configuredBankId.isBlank()) {
        throw new IllegalStateException("Connector bank id must not be blank");
      }
      BankId bankId;
      try {
        bankId = BankId.fromValue(configuredBankId);
      } catch (IllegalArgumentException ex) {
        throw new IllegalStateException(
            "Unsupported bank connector id: " + configuredBankId);
      }
      IPaymentProcessor previous = byBankId.putIfAbsent(bankId, connector);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate bank connector id: " + bankId.getValue());
      }
    }
    this.connectors = Map.copyOf(byBankId);
  }

  /**
   * Resolves the connector for a bank id.
   *
   * @param bankId selected bank id
   * @return connector implementation
   */
  public IPaymentProcessor get(BankId bankId) {
    IPaymentProcessor connector = bankId == null ? null : connectors.get(bankId);
    if (connector == null) {
      throw new UnsupportedBankException(
          connectors.keySet().stream().map(BankId::getValue).toList());
    }
    return connector;
  }
}
