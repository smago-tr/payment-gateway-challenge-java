package com.checkout.payment.gateway.security;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// Stand-in for real authentication. Production would resolve the key (or an OAuth2 token)
// to a merchant and scope every payment lookup to that merchant.
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

  static final String API_KEY_HEADER = "X-API-Key";

  private final byte[] apiKey;
  private final ObjectMapper objectMapper;

  public ApiKeyInterceptor(@Value("${gateway.api-key}") String apiKey, ObjectMapper objectMapper) {
    this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) throws IOException {
    String provided = request.getHeader(API_KEY_HEADER);
    if (provided != null && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), apiKey)) {
      return true;
    }
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), new ErrorResponse("Missing or invalid API key"));
    return false;
  }
}
