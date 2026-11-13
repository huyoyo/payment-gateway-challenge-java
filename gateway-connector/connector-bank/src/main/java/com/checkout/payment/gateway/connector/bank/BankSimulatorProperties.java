package com.checkout.payment.gateway.connector.bank;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the bank simulator connector. The bank id is configurable so the same code can
 * be deployed under different routing identifiers if required.
 */
@ConfigurationProperties(prefix = "connector.bank.simulator")
public class BankSimulatorProperties {

  private String bankId = "bank-simulator";
  private String baseUrl = "http://localhost:8080";
  private Duration connectTimeout = Duration.ofSeconds(2);
  private Duration readTimeout = Duration.ofSeconds(3);

  public String getBankId() {
    return bankId;
  }

  public void setBankId(String bankId) {
    this.bankId = bankId;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }
}
