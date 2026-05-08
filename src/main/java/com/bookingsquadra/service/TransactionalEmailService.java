package com.bookingsquadra.service;

import com.bookingsquadra.dto.email.TemplateEmailRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TransactionalEmailService {

    private final TaskExecutor emailTaskExecutor;
    private final MailgunTemplateSender mailgunTemplateSender;

    public TransactionalEmailService(
            @Qualifier("emailTaskExecutor") TaskExecutor emailTaskExecutor,
            MailgunTemplateSender mailgunTemplateSender) {
        this.emailTaskExecutor = emailTaskExecutor;
        this.mailgunTemplateSender = mailgunTemplateSender;
    }

    public void scheduleAfterCommit(TemplateEmailRequest request) {
        Runnable task = () -> mailgunTemplateSender.send(request);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailTaskExecutor.execute(task);
                }
            });
        } else {
            emailTaskExecutor.execute(task);
        }
    }
}
