package com.iflytek.skillhub.auth.privatesso;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnProperty(prefix = "skillhub.auth.private-sso", name = "base-url")
@EnableConfigurationProperties(PrivateSsoProperties.class)
public class PrivateSsoConfig {

    @Bean
    public WebClient privateSsoWebClient(PrivateSsoProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Bean
    public PrivateSsoClient privateSsoClient(WebClient privateSsoWebClient,
                                              PrivateSsoProperties properties,
                                              ObjectMapper objectMapper) {
        return new PrivateSsoClient(privateSsoWebClient, properties, objectMapper);
    }

    @Bean
    public PrivateSsoIdentityService privateSsoIdentityService(
            com.iflytek.skillhub.auth.identity.IdentityBindingService identityBindingService) {
        return new PrivateSsoIdentityService(identityBindingService);
    }

    @Bean
    public PrivateSsoDirectAuthProvider privateSsoDirectAuthProvider(
            PrivateSsoClient client,
            PrivateSsoIdentityService identityService,
            PrivateSsoProperties properties) {
        return new PrivateSsoDirectAuthProvider(client, identityService, properties.getIdentity());
    }
}
