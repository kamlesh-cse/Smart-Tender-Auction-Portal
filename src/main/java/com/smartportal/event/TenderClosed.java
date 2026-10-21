package com.smartportal.event;

import java.time.LocalDateTime;

public record TenderClosed(Long tenderId, String winner, LocalDateTime occurredAt)
        implements DomainEvent {
}
