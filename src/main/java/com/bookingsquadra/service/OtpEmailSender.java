package com.bookingsquadra.service;

import com.bookingsquadra.config.MailgunProperties;
import com.mailgun.api.v3.MailgunMessagesApi;
import com.mailgun.model.message.Message;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class OtpEmailSender {

    private final MailgunMessagesApi mailgunMessagesApi;
    private final MailgunProperties props;

    public OtpEmailSender(MailgunMessagesApi mailgunMessagesApi, MailgunProperties props) {
        this.mailgunMessagesApi = mailgunMessagesApi;
        this.props = props;
    }

    public void sendDeleteAccountOtp(String to, String code) {
        Message message = Message.builder()
                .from(props.from())
                .to(to)
                .subject("Confirme a exclusão da sua conta Squadra")
                .template(props.otpTemplate())
                .mailgunVariables(Map.of("otp_code", code))
                .build();

        try {
            mailgunMessagesApi.sendMessage(props.domain(), message);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed to deliver OTP email", e);
        }
    }

    public void sendLoginOtp(String to, String code) {
        Message message = Message.builder()
                .from(props.from())
                .to(to)
                .subject("Seu código de verificação Squadra")
                .template(props.otpTemplate())
                .mailgunVariables(Map.of("otp_code", code))
                .build();

        try {
            mailgunMessagesApi.sendMessage(props.domain(), message);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed to deliver OTP email", e);
        }
    }
}
