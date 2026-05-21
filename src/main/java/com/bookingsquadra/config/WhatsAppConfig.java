package com.bookingsquadra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({WhatsAppProperties.class, PhoneAuthProperties.class})
public class WhatsAppConfig {

    /**
     * Always-available RestClient for Meta's Graph API. Configuration may be empty in dev —
     * the sender checks {@link WhatsAppProperties#isConfigured()} and fails at send time
     * rather than at startup so the app still boots without WhatsApp credentials.
     */
    @Bean
    RestClient whatsAppRestClient(WhatsAppProperties props) {
        String baseUrl = props.isConfigured()
                ? props.baseUrl() + "/" + props.phoneNumberId()
                : "https://graph.facebook.com/v18.0/placeholder";
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (props.accessToken() != null && !props.accessToken().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + props.accessToken());
        }
        return builder.build();
    }
}
