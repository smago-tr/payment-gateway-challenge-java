package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.ErrorResponse.FieldError;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExpiryDateValidator {

  private final Clock clock;

  public ExpiryDateValidator(Clock clock) {
    this.clock = clock;
  }

  public void validate(PostPaymentRequest request) {
    YearMonth expiry = YearMonth.of(request.getExpiryYear(), request.getExpiryMonth());
    if (!expiry.isAfter(YearMonth.now(clock))) {
      throw new PaymentRejectedException(
          List.of(new FieldError("expiry_month", "expiry date must be in the future")));
    }
  }
}
