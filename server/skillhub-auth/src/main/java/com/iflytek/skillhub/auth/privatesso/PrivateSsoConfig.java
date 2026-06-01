package com.iflytek.skillhub.auth.privatesso;

import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@ConditionalOnProperty(prefix = "skillhub.auth.private-sso", name = "base-url")
@EnableConfigurationProperties(PrivateSsoProperties.class)
public class PrivateSsoConfig {

    @Bean
    public WebClient privateSsoWebClient(PrivateSsoProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) properties.getConnectTimeout().toMillis())
                .responseTimeout(properties.getReadTimeout());

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public PrivateSsoClient privateSsoClient(WebClient privateSsoWebClient) {
        return new PrivateSsoClient(privateSsoWebClient);
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
