package com.checkout.payment.gateway.configuration;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ApplicationConfiguration {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder,
      @Value("${bank.connect-timeout}") Duration connectTimeout,
      @Value("${bank.read-timeout}") Duration readTimeout) {
    return builder
        .setConnectTimeout(connectTimeout)
        .setReadTimeout(readTimeout)
        .build();
  }


  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  // A JSON number cannot carry leading zeros, so numbers are not accepted for text fields.
  // Ignoring this will make CVV 0123 int 123 which would pass validation but is otherwise incorrect
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer strictTextFields() {
    return builder -> builder.postConfigurer(mapper ->
        mapper.coercionConfigFor(LogicalType.Textual)
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
  }
}
