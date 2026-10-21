package com.smartportal.repository;

import com.smartportal.model.EmailNotification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {
    Optional<EmailNotification> findByIdempotencyKey(String idempotencyKey);
    long countByStatus(String status);
}
