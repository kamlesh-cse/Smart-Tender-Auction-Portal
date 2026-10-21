package com.smartportal.service;

import com.smartportal.model.EmailNotification;
import com.smartportal.repository.EmailNotificationRepository;
import io.micrometer.core.instrument.Metrics;
import java.time.LocalDateTime;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryService {
    private final EmailNotificationRepository repository;
    private final EmailService emailService;

    public EmailDeliveryService(EmailNotificationRepository repository, EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    @Retryable(retryFor = RuntimeException.class, maxAttemptsExpression = "${app.retry.email.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.email.delay-ms:1000}", multiplier = 2))
    @Transactional
    public void deliver(Long notificationId) {
        EmailNotification notification = repository.findById(notificationId).orElseThrow();
        if ("SENT".equals(notification.getStatus())) return;
        notification.setAttempts(notification.getAttempts() + 1);
        repository.save(notification);
        send(notification);
        notification.setStatus("SENT");
        notification.setSentAt(LocalDateTime.now());
        notification.setLastError(null);
        repository.save(notification);
        Metrics.counter("smartportal.email.sent", "type", notification.getNotificationType()).increment();
    }

    @Recover
    @Transactional
    public void recover(RuntimeException failure, Long notificationId) {
        repository.findById(notificationId).ifPresent(notification -> {
            notification.setStatus("FAILED");
            notification.setLastError(failure.getMessage());
            repository.save(notification);
            Metrics.counter("smartportal.email.failed", "type", notification.getNotificationType()).increment();
        });
    }

    private void send(EmailNotification n) {
        switch (n.getNotificationType()) {
            case "LOGIN" -> emailService.sendLoginEmail(n.getRecipient(), n.getRecipientName(), n.getRelatedName());
            case "SIGNUP" -> emailService.sendSignupEmail(n.getRecipient(), n.getRecipientName(), n.getEntityType());
            case "AUCTION_CREATED" -> emailService.sendAuctionCreatedEmail(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getRelatedName());
            case "TENDER_CREATED" -> emailService.sendTenderCreatedEmail(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getRelatedName());
            case "AUCTION_BID" -> emailService.sendBidPlacedEmail(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getAmount());
            case "AUCTION_BID_ALERT" -> emailService.sendBidAlertToCreator(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getRelatedName(), n.getAmount());
            case "TENDER_BID" -> emailService.sendTenderSubmittedEmail(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getAmount());
            case "TENDER_BID_ALERT" -> emailService.sendTenderAlertToCreator(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getRelatedName(), n.getAmount());
            case "AUCTION_WINNER" -> emailService.sendAuctionWinnerEmail(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getAmount());
            case "TENDER_WINNER" -> emailService.sendTenderWinnerEmail(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getAmount());
            case "RESULT_CREATOR" -> emailService.sendResultToCreator(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getRelatedName(),
                    n.getAmount(), n.getEntityType());
            case "LOSER" -> emailService.sendLoserNotification(
                    n.getRecipient(), n.getRecipientName(), n.getTitle(), n.getEntityType());
            default -> throw new IllegalArgumentException("Unknown notification type " + n.getNotificationType());
        }
    }
}
