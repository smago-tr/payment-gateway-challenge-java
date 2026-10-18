package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {
  private final String message;
  private final List<FieldError> errors;

  public ErrorResponse(String message) {
    this(message, List.of());
  }

  public ErrorResponse(String message, List<FieldError> errors) {
    this.message = message;
    this.errors = errors;
  }

  public String getMessage() {
    return message;
  }

  public List<FieldError> getErrors() {
    return errors;
  }

  public record FieldError(String field, String message) {

  }

  @Override
  public String toString() {
    return "ErrorResponse{" +
        "message='" + message + '\'' +
        ", errors=" + errors +
        '}';
  }
}
