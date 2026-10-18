package com.checkout.payment.gateway.configuration;

import com.checkout.payment.gateway.security.ApiKeyInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiSecurityConfiguration implements WebMvcConfigurer {

  private final ApiKeyInterceptor apiKeyInterceptor;

  public ApiSecurityConfiguration(ApiKeyInterceptor apiKeyInterceptor) {
    this.apiKeyInterceptor = apiKeyInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(apiKeyInterceptor).addPathPatterns("/payment", "/payment/**");
  }

  // Adds the "Authorize" button to Swagger UI so the secured endpoints can still be tried
  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .components(new Components().addSecuritySchemes("apiKey", new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name("X-API-Key")))
        .addSecurityItem(new SecurityRequirement().addList("apiKey"));
  }
}
