package com.smartportal.event;

import com.smartportal.repository.EmailNotificationRepository;
import com.smartportal.service.EmailService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DomainEventPublisherIntegrationTest {

    @Autowired private DomainEventPublisher events;
    @Autowired private EmailNotificationRepository repository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private EmailService emailService;

    @Test
    void afterCommitEmailNotificationPersistsInNewTransaction() {
        String key = "test:after-commit:" + System.nanoTime();

        transactionTemplate.executeWithoutResult(status ->
                events.publishAfterCommit(new EmailNotificationRequested(
                        key,
                        "SIGNUP",
                        "after-commit@example.com",
                        "After Commit",
                        null,
                        null,
                        null,
                        "USER",
                        LocalDateTime.now())));

        assertTrue(repository.findByIdempotencyKey(key).isPresent());
    }
}
