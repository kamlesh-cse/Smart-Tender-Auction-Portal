package com.smartportal.service;

import com.smartportal.exception.ForbiddenException;
import com.smartportal.exception.ValidationException;
import com.smartportal.model.Auction;
import com.smartportal.model.Tender;
import com.smartportal.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Component
public class BusinessRuleValidator {

    private static final int MIN_TENDER_DAYS = 3;
    private static final int MIN_AUCTION_MINUTES = 30;

    private final AuthorizationService authorizationService;
    private final Clock clock;

    @Autowired
    public BusinessRuleValidator(AuthorizationService authorizationService) {
        this(authorizationService, Clock.systemDefaultZone());
    }

    BusinessRuleValidator(AuthorizationService authorizationService, Clock clock) {
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    public void validateTenderDuration(Tender tender) {
        if (tender.getStartDate() == null || tender.getEndDate() == null) {
            return;
        }

        if (tender.getEndDate().isBefore(LocalDate.now(clock))) {
            throw new ValidationException("Tender end date cannot be in the past.");
        }

        long days = ChronoUnit.DAYS.between(tender.getStartDate(), tender.getEndDate());
        if (days < MIN_TENDER_DAYS) {
            throw new ValidationException("Tender duration must be at least 3 days.");
        }
    }

    public void validateAuctionDuration(Auction auction) {
        if (auction.getDurationMinutes() != null && auction.getDurationMinutes() < MIN_AUCTION_MINUTES) {
            throw new ValidationException("Auction duration must be at least 30 minutes.");
        }
    }

    public void validateTenderEditAllowed(Tender existingTender, User actor) {
        if (authorizationService.isAdmin(actor) || existingTender.getStartDate() == null) {
            return;
        }

        LocalDateTime lockTime = existingTender.getStartDate().atStartOfDay().minusHours(24);
        if (!LocalDateTime.now(clock).isBefore(lockTime)) {
            throw new ForbiddenException("Cannot edit a tender within 24 hours before its start time.");
        }
    }

    public void validateTenderDeleteAllowed(Tender existingTender, User actor) {
        if (authorizationService.isAdmin(actor)) {
            return;
        }
        validateTenderEditAllowed(existingTender, actor);
    }

    public void validateAuctionEditAllowed(Auction existingAuction, User actor) {
        if (authorizationService.isAdmin(actor) || existingAuction.getAuctionDate() == null
                || existingAuction.getAuctionTime() == null) {
            return;
        }

        LocalDateTime lockTime = LocalDateTime.of(
                existingAuction.getAuctionDate(),
                existingAuction.getAuctionTime()).minusHours(1);
        if (!LocalDateTime.now(clock).isBefore(lockTime)) {
            throw new ForbiddenException("Cannot edit an auction within 1 hour before its start time.");
        }
    }

    public void validateAuctionDeleteAllowed(Auction existingAuction, User actor) {
        if (authorizationService.isAdmin(actor)) {
            return;
        }
        validateAuctionEditAllowed(existingAuction, actor);
    }

    public LocalDateTime tenderDeadline(Tender tender) {
        return tender.getEndDate() == null ? null : tender.getEndDate().atTime(LocalTime.MAX);
    }
}
