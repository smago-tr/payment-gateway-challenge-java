package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse.FieldError;
import java.util.List;

public class PaymentRejectedException extends RuntimeException {

  private final List<FieldError> errors;

  public PaymentRejectedException(List<FieldError> errors) {
    super("Payment rejected");
    this.errors = errors;
  }

  public List<FieldError> getErrors() {
    return errors;
  }
}
