package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;

public class PostPaymentRequest implements Serializable {

  @NotNull
  @JsonProperty("card_number")
  @Pattern(regexp = "[0-9]{14,19}", message = "must be 14 to 19 digits")
  // Changed to integer as int only allows 10 digits max and crops zeros
  private String cardNumber;

  @NotNull
  @JsonProperty("expiry_month")
  @Min(value = 1, message = "must be between 1 and 12")
  @Max(value = 12, message = "must be between 1 and 12")
  // Changed to integer as int so a missing value is null not 0
  private Integer expiryMonth;

  @NotNull
  @JsonProperty("expiry_year")
  private Integer expiryYear;

  @NotNull
  // USD, EUR, GBP
  @Pattern(regexp = "GBP|EUR|USD", message = "must be one of GBP, EUR, USD")
  private String currency;

  @NotNull
  @Min(value = 1, message = "must be at least 1")
  private Integer amount;

  @NotNull
  @Pattern(regexp = "[0-9]{3,4}", message = "must be 3 to 4 digits")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
