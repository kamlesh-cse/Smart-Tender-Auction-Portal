package com.smartportal.event;

import java.time.LocalDateTime;

public record EmailNotificationRequested(
        String idempotencyKey,
        String notificationType,
        String recipient,
        String recipientName,
        String title,
        String relatedName,
        Double amount,
        String entityType,
        LocalDateTime occurredAt) implements DomainEvent {
}
