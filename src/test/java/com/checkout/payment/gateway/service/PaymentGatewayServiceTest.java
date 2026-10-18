package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankErrorException;
import com.checkout.payment.gateway.exception.BankTimeoutException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class PaymentGatewayServiceTest {

  private final PaymentsRepository repository = mock(PaymentsRepository.class);
  private final ExpiryDateValidator expiryDateValidator = mock(ExpiryDateValidator.class);
  private final AcquiringBankClient bankClient = mock(AcquiringBankClient.class);
  private final PaymentGatewayService service =
      new PaymentGatewayService(repository, expiryDateValidator, bankClient);

  @Test
  void whenProcessingPaymentThenExpiryDateIsValidated() {
    PostPaymentRequest request = payment();
    when(bankClient.authorize(request)).thenReturn(PaymentStatus.AUTHORIZED);

    service.processPayment(request);

    verify(expiryDateValidator).validate(request);
  }

  @Test
  void whenExpiryDateIsRejectedThenBankIsNotCalledAndPaymentIsNotStored() {
    PostPaymentRequest request = payment();
    doThrow(new PaymentRejectedException(List.of()))
        .when(expiryDateValidator).validate(request);

    assertThrows(PaymentRejectedException.class, () -> service.processPayment(request));

    verifyNoInteractions(bankClient, repository);
  }

  @ParameterizedTest
  @EnumSource(value = PaymentStatus.class, names = {"AUTHORIZED", "DECLINED"})
  void whenBankRespondsThenPaymentIsStoredAndReturnedWithTheBankStatus(PaymentStatus status) {
    PostPaymentRequest request = payment();
    when(bankClient.authorize(request)).thenReturn(status);

    PostPaymentResponse result = service.processPayment(request);

    assertNotNull(result.getId());
    assertEquals(status, result.getStatus());
    assertEquals("1111", result.getCardNumberLastFour());
    assertEquals(12, result.getExpiryMonth());
    assertEquals(2099, result.getExpiryYear());
    assertEquals("USD", result.getCurrency());
    assertEquals(100, result.getAmount());

    ArgumentCaptor<PostPaymentResponse> stored = ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(repository).add(stored.capture());
    assertSame(result, stored.getValue());
  }

  @Test
  void whenCardNumberEndsInZerosThenLastFourKeepsLeadingZeros() {
    PostPaymentRequest request = payment();
    request.setCardNumber("4111111111110042");
    when(bankClient.authorize(request)).thenReturn(PaymentStatus.AUTHORIZED);

    assertEquals("0042", service.processPayment(request).getCardNumberLastFour());
  }

  static Stream<Arguments> bankFailures() {
    return Stream.of(
        Arguments.of(new BankUnavailableException("down", null)),
        Arguments.of(new BankTimeoutException("slow", null)),
        Arguments.of(new BankErrorException("bad request")));
  }

  @ParameterizedTest
  @MethodSource("bankFailures")
  void whenBankFailsThenPaymentIsNotStoredAndFailureIsPropagated(RuntimeException failure) {
    PostPaymentRequest request = payment();
    when(bankClient.authorize(request)).thenThrow(failure);

    assertSame(failure,
        assertThrows(failure.getClass(), () -> service.processPayment(request)));

    verifyNoInteractions(repository);
  }

  @Test
  void whenPaymentExistsThenItIsReturnedById() {
    PostPaymentResponse stored = new PostPaymentResponse();
    UUID id = UUID.randomUUID();
    when(repository.get(id)).thenReturn(java.util.Optional.of(stored));

    assertSame(stored, service.getPaymentById(id));
  }

  @Test
  void whenPaymentDoesNotExistThenNotFoundIsThrown() {
    when(repository.get(any())).thenReturn(java.util.Optional.empty());

    assertThrows(EventProcessingException.class, () -> service.getPaymentById(UUID.randomUUID()));
  }

  private PostPaymentRequest payment() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("4111111111111111");
    request.setExpiryMonth(12);
    request.setExpiryYear(2099);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}
