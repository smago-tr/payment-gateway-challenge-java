package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpiryDateValidatorTest {

  @ParameterizedTest
  @CsvSource({
      "2026-10-18T10:00:00Z, 10, 2026",
      "2026-10-18T10:00:00Z, 9, 2026",
      "2026-10-18T10:00:00Z, 12, 2025",
      "2026-10-18T10:00:00Z, 1, 2000",
      "2026-12-31T23:59:59Z, 12, 2026"})
  void whenExpiryIsNotInAFutureMonthThenPaymentIsRejected(String now, int month, int year) {
    ExpiryDateValidator validator = validatorAt(now);

    PaymentRejectedException exception = assertThrows(PaymentRejectedException.class,
        () -> validator.validate(paymentExpiring(month, year)));

    assertEquals(
        List.of(new ErrorResponse.FieldError("expiry_month", "expiry date must be in the future")),
        exception.getErrors());
  }

  @ParameterizedTest
  @CsvSource({
      "2026-10-18T10:00:00Z, 11, 2026",
      "2026-10-18T10:00:00Z, 1, 2027",
      "2026-10-18T10:00:00Z, 12, 2099",
      "2026-12-31T23:59:59Z, 1, 2027"})
  void whenExpiryIsInAFutureMonthThenPaymentIsNotRejected(String now, int month, int year) {
    ExpiryDateValidator validator = validatorAt(now);

    assertDoesNotThrow(() -> validator.validate(paymentExpiring(month, year)));
  }

  private ExpiryDateValidator validatorAt(String now) {
    return new ExpiryDateValidator(Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
  }

  private PostPaymentRequest paymentExpiring(int month, int year) {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setExpiryMonth(month);
    request.setExpiryYear(year);
    return request;
  }
}
