package com.smartportal.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationDispatcher {
    private final EmailDeliveryService deliveryService;

    public EmailNotificationDispatcher(EmailDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Async("notificationExecutor")
    public void dispatch(Long notificationId) {
        deliveryService.deliver(notificationId);
    }
}
