package com.smartportal.event;

import java.time.LocalDateTime;

public record AuctionClosed(Long auctionId, String winner, LocalDateTime occurredAt)
        implements DomainEvent {
}
