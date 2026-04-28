package com.bookingsquadra.config;

import com.mailgun.api.v3.MailgunMessagesApi;
import com.mailgun.client.MailgunClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MailgunProperties.class, PaymentWebhookProperties.class})
public class MailgunConfig {

    @Bean
    MailgunMessagesApi mailgunMessagesApi(MailgunProperties props) {
        return MailgunClient
                .config(props.baseUrl(), props.apiKey())
                .createApi(MailgunMessagesApi.class);
    }
}
