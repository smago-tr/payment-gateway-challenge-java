package com.checkout.payment.gateway.exception;

public class BankErrorException extends RuntimeException {

  public BankErrorException(String message) {
    super(message);
  }

  public BankErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
