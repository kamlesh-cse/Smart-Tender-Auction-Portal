package com.smartportal.monitoring;

import com.smartportal.repository.EmailNotificationRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("smartPortalReliability")
public class ReliabilityHealthIndicator implements HealthIndicator {
    private final EmailNotificationRepository notifications;
    private final WebSocketMetrics webSocketMetrics;

    public ReliabilityHealthIndicator(EmailNotificationRepository notifications,
            WebSocketMetrics webSocketMetrics) {
        this.notifications = notifications;
        this.webSocketMetrics = webSocketMetrics;
    }

    @Override
    public Health health() {
        long failed = notifications.countByStatus("FAILED");
        Health.Builder builder = failed > 100 ? Health.down() : Health.up();
        return builder
                .withDetail("failedEmails", failed)
                .withDetail("pendingEmails", notifications.countByStatus("PENDING"))
                .withDetail("activeWebSocketConnections", webSocketMetrics.activeConnections())
                .build();
    }
}
