package com.bookingsquadra.service;

import com.bookingsquadra.config.MailgunProperties;
import com.bookingsquadra.dto.email.EmailSubjectTemplates;
import com.bookingsquadra.dto.email.PaymentConfirmedEmailPayload;
import com.bookingsquadra.dto.email.PaymentRefundDeniedEmailPayload;
import com.bookingsquadra.dto.email.PaymentRefundInProgressEmailPayload;
import com.bookingsquadra.dto.email.PaymentRefundedEmailPayload;
import com.bookingsquadra.dto.email.PrereservationCancelledEmailPayload;
import com.bookingsquadra.dto.email.TemplateEmailRequest;
import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.Payment;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.Venue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

@Component
public class BookingEmailPayloadMapper {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(PT_BR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withLocale(PT_BR);

    private final MailgunProperties mailgunProperties;

    public BookingEmailPayloadMapper(MailgunProperties mailgunProperties) {
        this.mailgunProperties = mailgunProperties;
    }

    public TemplateEmailRequest paymentConfirmed(BookingNotificationData data) {
        PaymentConfirmedEmailPayload payload = new PaymentConfirmedEmailPayload(
                data.dataReserva(),
                data.horaReserva(),
                data.venueName(),
                data.userName(),
                data.courtName()
        );
        return new TemplateEmailRequest(
                data.recipientEmail(),
                mailgunProperties.templatePaymentConfirmed(),
                EmailSubjectTemplates.interpolateNomeDaQuadra(
                        EmailSubjectTemplates.PAYMENT_CONFIRMED,
                        data.venueName()),
                payload.toTemplateVariables()
        );
    }

    public TemplateEmailRequest refundInProgress(BookingNotificationData data, int refundAmountCents) {
        PaymentRefundInProgressEmailPayload payload = new PaymentRefundInProgressEmailPayload(
                data.dataReserva(),
                data.horaReserva(),
                data.venueName(),
                data.userName(),
                formatBrl(refundAmountCents)
        );
        return new TemplateEmailRequest(
                data.recipientEmail(),
                mailgunProperties.templatePaymentRefundInProgress(),
                EmailSubjectTemplates.interpolateNomeDaQuadra(
                        EmailSubjectTemplates.PAYMENT_REFUND_IN_PROGRESS,
                        data.venueName()),
                payload.toTemplateVariables()
        );
    }

    public TemplateEmailRequest refunded(BookingNotificationData data, int refundAmountCents) {
        PaymentRefundedEmailPayload payload = new PaymentRefundedEmailPayload(
                data.dataReserva(),
                data.venueName(),
                data.userName(),
                formatBrl(refundAmountCents)
        );
        return new TemplateEmailRequest(
                data.recipientEmail(),
                mailgunProperties.templatePaymentRefunded(),
                EmailSubjectTemplates.interpolateNomeDaQuadra(
                        EmailSubjectTemplates.PAYMENT_REFUNDED,
                        data.venueName()),
                payload.toTemplateVariables()
        );
    }

    public TemplateEmailRequest refundDenied(BookingNotificationData data) {
        PaymentRefundDeniedEmailPayload payload = new PaymentRefundDeniedEmailPayload(
                data.dataReserva(),
                data.horaReserva(),
                data.venueName(),
                data.userName()
        );
        return new TemplateEmailRequest(
                data.recipientEmail(),
                mailgunProperties.templatePaymentRefundDenied(),
                EmailSubjectTemplates.interpolateNomeDaQuadra(
                        EmailSubjectTemplates.PAYMENT_REFUND_DENIED,
                        data.venueName()),
                payload.toTemplateVariables()
        );
    }

    public TemplateEmailRequest prereservationCancelled(BookingNotificationData data) {
        PrereservationCancelledEmailPayload payload = new PrereservationCancelledEmailPayload(
                data.dataReserva(),
                data.horaReserva(),
                data.venueName(),
                data.userName()
        );
        return new TemplateEmailRequest(
                data.recipientEmail(),
                mailgunProperties.templatePrereservationCancelled(),
                EmailSubjectTemplates.interpolateNomeDaQuadra(
                        EmailSubjectTemplates.PRERESERVATION_CANCELLED,
                        data.venueName()),
                payload.toTemplateVariables()
        );
    }

    public static BookingNotificationData fromEntities(
            Booking booking,
            User user,
            Court court,
            Venue venue
    ) {
        ZoneId zone = ZoneId.of(booking.getVenueTimezone());
        var zonedStart = booking.getStartsAt().atZoneSameInstant(zone);
        String dataReserva = zonedStart.format(DATE);
        String horaReserva = zonedStart.format(TIME);
        String email = Objects.requireNonNullElse(user.getEmail(), "").trim();
        String userName = Objects.requireNonNullElse(user.getName(), "").trim();
        return new BookingNotificationData(
                email,
                userName,
                venue.getName(),
                court.getName(),
                dataReserva,
                horaReserva
        );
    }

    public static int resolveRefundDisplayCents(Payment payment) {
        if (payment.getRefundAmountCents() != null && payment.getRefundAmountCents() > 0) {
            return payment.getRefundAmountCents();
        }
        return payment.getAmountCents() != null ? payment.getAmountCents() : 0;
    }

    private static String formatBrl(int cents) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(PT_BR);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(2);
        return nf.format(BigDecimal.valueOf(cents, 2));
    }

    public record BookingNotificationData(
            String recipientEmail,
            String userName,
            String venueName,
            String courtName,
            String dataReserva,
            String horaReserva
    ) {
    }
}
