package com.smartportal.service;

import com.smartportal.event.EmailNotificationRequested;
import com.smartportal.model.EmailNotification;
import com.smartportal.repository.EmailNotificationRepository;
import java.time.LocalDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EmailNotificationListener {
    private final EmailNotificationRepository repository;
    private final EmailNotificationDispatcher dispatcher;

    public EmailNotificationListener(EmailNotificationRepository repository,
            EmailNotificationDispatcher dispatcher) {
        this.repository = repository;
        this.dispatcher = dispatcher;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEmailRequested(EmailNotificationRequested event) {
        if (repository.findByIdempotencyKey(event.idempotencyKey()).isPresent()) return;

        EmailNotification notification = new EmailNotification();
        notification.setIdempotencyKey(event.idempotencyKey());
        notification.setNotificationType(event.notificationType());
        notification.setRecipient(event.recipient());
        notification.setRecipientName(event.recipientName());
        notification.setTitle(event.title());
        notification.setRelatedName(event.relatedName());
        notification.setAmount(event.amount());
        notification.setEntityType(event.entityType());
        notification.setCreatedAt(LocalDateTime.now());
        try {
            notification = repository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException duplicate) {
            return;
        }
        dispatcher.dispatch(notification.getId());
    }
}
