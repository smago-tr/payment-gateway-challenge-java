package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);
  private static final SnakeCaseStrategy SNAKE_CASE = new SnakeCaseStrategy();

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex) {
    List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> new ErrorResponse.FieldError(
            // we keep it snake case as thats what the merchant will understand
            SNAKE_CASE.translate(error.getField()),
            error.getDefaultMessage()))
        .toList();
    return paymentRejected(errors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return new ResponseEntity<>(new ErrorResponse("Request body is missing or malformed"),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<ErrorResponse> handlePaymentRejectedException(
      PaymentRejectedException ex) {
    return paymentRejected(ex.getErrors());
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(BankUnavailableException ex) {
    LOG.error("Bank unavailable", ex);
    return new ResponseEntity<>(
        new ErrorResponse("The bank is unavailable, please try again in a few minutes"),
        HttpStatus.BAD_GATEWAY);
  }

  @ExceptionHandler(BankTimeoutException.class)
  public ResponseEntity<ErrorResponse> handleBankTimeout(BankTimeoutException ex) {
    LOG.error("Bank timed out", ex);
    return new ResponseEntity<>(new ErrorResponse("The bank did not respond in time"),
        HttpStatus.GATEWAY_TIMEOUT);
  }

  @ExceptionHandler(BankErrorException.class)
  public ResponseEntity<ErrorResponse> handleBankError(BankErrorException ex) {
    LOG.error("Bank returned an unusable response", ex);
    return new ResponseEntity<>(new ErrorResponse("Could not process the payment"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ErrorResponse> paymentRejected(List<ErrorResponse.FieldError> errors) {
    return new ResponseEntity<>(new ErrorResponse("Payment rejected", errors),
        HttpStatus.BAD_REQUEST);
  }
}
