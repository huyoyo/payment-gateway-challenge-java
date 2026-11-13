package com.checkout.payment.gateway.web;

import com.checkout.payment.gateway.connector.api.error.ApiErrorResponseBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Minimal HTTP Basic token check for the payment endpoints. The configured value is the Base64
 * token from the Authorization header, not a username/password pair.
 */
@Component("gateway.web.basic-token-authentication-filter")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BasicTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String BASIC_PREFIX = "Basic ";
  private static final String REALM = "Basic realm=\"payments\"";

  private final byte[] expectedToken;
  private final ApiErrorResponseBuilder errorResponseBuilder;
  private final ObjectMapper objectMapper;

  public BasicTokenAuthenticationFilter(
      @Value("${gateway.security.basic.token}") String expectedToken,
      ApiErrorResponseBuilder errorResponseBuilder,
      ObjectMapper objectMapper) {
    this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    this.errorResponseBuilder = errorResponseBuilder;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !(path.startsWith("/payments") || path.startsWith("/payment"));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null
        || !authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
      writeUnauthorized(response);
      return;
    }

    String suppliedToken = authorization.substring(BASIC_PREFIX.length()).trim();
    if (!MessageDigest.isEqual(expectedToken, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
      writeUnauthorized(response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private void writeUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, REALM);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(),
        errorResponseBuilder.error("UNAUTHORIZED", "Missing or invalid Basic token"));
  }
}
