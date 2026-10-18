package com.checkout.payment.gateway.exception;

public class BankTimeoutException extends RuntimeException {

  public BankTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
