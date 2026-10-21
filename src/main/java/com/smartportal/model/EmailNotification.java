package com.smartportal.model;

import com.smartportal.persistence.MonetaryDoubleConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_notification_key",
                columnNames = "idempotency_key"))
public class EmailNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;
    @Column(nullable = false) private String notificationType;
    @Column(nullable = false) private String recipient;
    private String recipientName;
    private String title;
    private String relatedName;
    @Convert(converter = MonetaryDoubleConverter.class)
    private Double amount;
    private String entityType;
    @Column(nullable = false) private String status = "PENDING";
    private int attempts;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    @Column(length = 1000) private String lastError;

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String value) { this.notificationType = value; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String value) { this.recipient = value; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String value) { this.recipientName = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getRelatedName() { return relatedName; }
    public void setRelatedName(String value) { this.relatedName = value; }
    public Double getAmount() { return amount; }
    public void setAmount(Double value) { this.amount = value; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String value) { this.entityType = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { this.attempts = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime value) { this.sentAt = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { this.lastError = value; }
}
