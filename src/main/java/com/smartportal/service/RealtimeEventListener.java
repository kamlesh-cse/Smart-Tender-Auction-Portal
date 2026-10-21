package com.smartportal.service;

import com.smartportal.event.AuctionClosed;
import com.smartportal.event.BidPlaced;
import com.smartportal.event.TenderClosed;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class RealtimeEventListener {
    private final BidBroadcastService broadcastService;

    public RealtimeEventListener(BidBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @EventListener
    @Retryable(retryFor = RuntimeException.class, maxAttemptsExpression = "${app.retry.websocket.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.websocket.delay-ms:250}", multiplier = 2))
    public void onBidPlaced(BidPlaced event) {
        broadcastService.broadcast(event);
    }

    @EventListener
    @Retryable(retryFor = RuntimeException.class, maxAttemptsExpression = "${app.retry.websocket.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.websocket.delay-ms:250}", multiplier = 2))
    public void onAuctionClosed(AuctionClosed event) {
        broadcastService.broadcast(event);
    }

    @EventListener
    @Retryable(retryFor = RuntimeException.class, maxAttemptsExpression = "${app.retry.websocket.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.websocket.delay-ms:250}", multiplier = 2))
    public void onTenderClosed(TenderClosed event) {
        broadcastService.broadcast(event);
    }
}
