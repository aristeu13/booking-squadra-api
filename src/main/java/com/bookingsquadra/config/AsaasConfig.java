package com.bookingsquadra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AsaasProperties.class)
public class AsaasConfig {

    private static final String ACCESS_TOKEN_HEADER = "access_token";

    @Bean
    RestClient asaasRestClient(AsaasProperties props) {
        if (props.accessToken() == null || props.accessToken().isBlank()) {
            throw new IllegalStateException("app.payments.asaas.access-token must be set");
        }
        if (props.baseUrl() == null || props.baseUrl().isBlank()) {
            throw new IllegalStateException("app.payments.asaas.base-url must be set");
        }
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultHeader(ACCESS_TOKEN_HEADER, props.accessToken())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
