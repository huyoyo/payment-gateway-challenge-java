package com.checkout.payment.gateway.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the source-of-truth OpenAPI contract used by the Swagger UI and code generation.
 */
@RestController("gateway.web.open-api-spec-controller")
public class OpenApiSpecController {

  @GetMapping(path = "/openapi.yaml", produces = "application/yaml")
  public Resource openApiSpec() {
    return new ClassPathResource("openapi/payment-gateway.yaml");
  }
}
