package com.bookingsquadra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments.asaas")
public record AsaasProperties(
        String baseUrl,
        String accessToken,
        Integer bookingGraceMinutes,
        Integer paymentWindowMinutes,
        Integer dueDays,
        Integer fullRefundFeeCents,
        Integer mainAccountShareCents
) {
    public int bookingGraceMinutesOrDefault() {
        return bookingGraceMinutes == null ? 3 : bookingGraceMinutes;
    }

    public int paymentWindowMinutesOrDefault() {
        return paymentWindowMinutes == null ? 10 : paymentWindowMinutes;
    }

    public int dueDaysOrDefault() {
        return dueDays == null ? 1 : dueDays;
    }

    public int fullRefundFeeCentsOrDefault() {
        return fullRefundFeeCents == null ? 199 : fullRefundFeeCents;
    }

    public int mainAccountShareCentsOrDefault() {
        return mainAccountShareCents == null ? 100 : mainAccountShareCents;
    }
}
