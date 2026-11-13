package com.checkout.payment.gateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds a correlation id to every request, returns it in {@code X-Request-Id} and puts it in the
 * logging MDC. The merchant-supplied value is echoed only when it is safe (no header injection).
 */
@Component("gateway.web.request-id-filter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  private static final Logger LOG = LoggerFactory.getLogger(RequestIdFilter.class);
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String requestId = request.getHeader(HEADER_NAME);
    if (!StringUtils.hasText(requestId) || !SAFE_REQUEST_ID.matcher(requestId).matches()) {
      requestId = UUID.randomUUID().toString();
    }

    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER_NAME, requestId);
    long startedAt = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
      LOG.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
          response.getStatus(), durationMillis);
      MDC.remove(MDC_KEY);
    }
  }
}
