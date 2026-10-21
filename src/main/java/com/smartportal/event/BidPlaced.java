package com.smartportal.event;

import com.smartportal.dto.BidMessage;
import java.time.LocalDateTime;

public record BidPlaced(
        String entityType,
        Long entityId,
        BidMessage payload,
        LocalDateTime occurredAt) implements DomainEvent {
}
