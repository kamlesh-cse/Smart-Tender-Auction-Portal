package com.smartportal.event;

import java.time.LocalDateTime;

public record WinnerDeclared(
        String entityType,
        Long entityId,
        Long winnerId,
        String winnerName,
        Double amount,
        LocalDateTime occurredAt) implements DomainEvent {
}
