package com.bookingsquadra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments.asaas")
public record AsaasProperties(
        String baseUrl,
        String accessToken,
        Integer paymentWindowMinutes,
        Integer dueDays,
        Integer fullRefundFeePercent
) {
    public int paymentWindowMinutesOrDefault() {
        return paymentWindowMinutes == null ? 10 : paymentWindowMinutes;
    }

    public int dueDaysOrDefault() {
        return dueDays == null ? 1 : dueDays;
    }

    public int fullRefundFeePercentOrDefault() {
        return fullRefundFeePercent == null ? 10 : fullRefundFeePercent;
    }
}
