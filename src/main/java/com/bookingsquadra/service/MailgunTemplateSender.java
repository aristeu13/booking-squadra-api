package com.bookingsquadra.service;

import com.bookingsquadra.config.MailgunProperties;
import com.bookingsquadra.dto.email.TemplateEmailRequest;
import com.mailgun.api.v3.MailgunMessagesApi;
import com.mailgun.model.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class MailgunTemplateSender {

    private static final Logger log = LoggerFactory.getLogger(MailgunTemplateSender.class);

    private final MailgunMessagesApi mailgunMessagesApi;
    private final MailgunProperties props;

    public MailgunTemplateSender(MailgunMessagesApi mailgunMessagesApi, MailgunProperties props) {
        this.mailgunMessagesApi = mailgunMessagesApi;
        this.props = props;
    }

    public void send(TemplateEmailRequest request) {
        try {
            Message message = Message.builder()
                    .from(props.from())
                    .to(request.to())
                    .subject(request.subject())
                    .template(request.templateId())
                    .mailgunVariables(new HashMap<>(request.templateVariables()))
                    .build();
            mailgunMessagesApi.sendMessage(props.domain(), message);
        } catch (RuntimeException e) {
            log.error("Failed to send transactional email templateId={} to={}",
                    request.templateId(), request.to(), e);
        }
    }
}
