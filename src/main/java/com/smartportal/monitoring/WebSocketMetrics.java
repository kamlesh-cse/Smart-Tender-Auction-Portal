package com.smartportal.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketMetrics {
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final MeterRegistry registry;

    public WebSocketMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("smartportal.websocket.connections.active", activeConnections);
    }

    @EventListener
    public void connected(SessionConnectEvent event) {
        activeConnections.incrementAndGet();
        registry.counter("smartportal.websocket.connections", "event", "connected").increment();
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
        registry.counter("smartportal.websocket.connections", "event", "disconnected").increment();
    }

    public int activeConnections() {
        return activeConnections.get();
    }
}
