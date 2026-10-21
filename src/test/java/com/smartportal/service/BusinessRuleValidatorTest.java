package com.smartportal.service;

import com.smartportal.exception.ForbiddenException;
import com.smartportal.exception.ValidationException;
import com.smartportal.model.Auction;
import com.smartportal.model.Tender;
import com.smartportal.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class BusinessRuleValidatorTest {

    private BusinessRuleValidator validator;
    private User adminUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2030-06-15T12:00:00Z"), ZoneId.of("UTC"));
        AuthorizationService authService = new AuthorizationService();
        validator = new BusinessRuleValidator(authService, fixed);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setRole("ADMIN");

        normalUser = new User();
        normalUser.setId(2L);
        normalUser.setRole("COMPANY");
    }

    @Nested
    @DisplayName("Tender duration validation")
    class TenderDuration {

        @Test
        void validDuration_passes() {
            Tender t = new Tender();
            t.setStartDate(LocalDate.of(2030, 6, 20));
            t.setEndDate(LocalDate.of(2030, 6, 25));
            assertDoesNotThrow(() -> validator.validateTenderDuration(t));
        }

        @Test
        void durationLessThan3Days_throws() {
            Tender t = new Tender();
            t.setStartDate(LocalDate.of(2030, 6, 20));
            t.setEndDate(LocalDate.of(2030, 6, 21));
            assertThrows(ValidationException.class, () -> validator.validateTenderDuration(t));
        }

        @Test
        void endDateInPast_throws() {
            Tender t = new Tender();
            t.setStartDate(LocalDate.of(2030, 6, 1));
            t.setEndDate(LocalDate.of(2030, 6, 10)); // before 2030-06-15 (fixed clock)
            assertThrows(ValidationException.class, () -> validator.validateTenderDuration(t));
        }

        @Test
        void nullDates_skipsValidation() {
            Tender t = new Tender();
            assertDoesNotThrow(() -> validator.validateTenderDuration(t));
        }

        @Test
        void exactly3Days_passes() {
            Tender t = new Tender();
            t.setStartDate(LocalDate.of(2030, 6, 20));
            t.setEndDate(LocalDate.of(2030, 6, 23));
            assertDoesNotThrow(() -> validator.validateTenderDuration(t));
        }
    }

    @Nested
    @DisplayName("Auction duration validation")
    class AuctionDuration {

        @Test
        void validDuration_passes() {
            Auction a = new Auction();
            a.setDurationMinutes(60);
            assertDoesNotThrow(() -> validator.validateAuctionDuration(a));
        }

        @Test
        void durationLessThan30_throws() {
            Auction a = new Auction();
            a.setDurationMinutes(15);
            assertThrows(ValidationException.class, () -> validator.validateAuctionDuration(a));
        }

        @Test
        void nullDuration_skips() {
            Auction a = new Auction();
            a.setDurationMinutes(null);
            assertDoesNotThrow(() -> validator.validateAuctionDuration(a));
        }

        @Test
        void exactly30Minutes_passes() {
            Auction a = new Auction();
            a.setDurationMinutes(30);
            assertDoesNotThrow(() -> validator.validateAuctionDuration(a));
        }
    }

    @Nested
    @DisplayName("Tender edit-lock validation")
    class TenderEditLock {

        @Test
        void adminBypassesEditLock() {
            Tender t = new Tender();
            t.setStartDate(LocalDate.of(2030, 6, 15)); // today = within 24h
            assertDoesNotThrow(() -> validator.validateTenderEditAllowed(t, adminUser));
        }

        @Test
        void editAllowedWellBeforeStart() {
            Tender t = new Tender();
            t.setStartDate(LocalDate.of(2030, 6, 20)); // 5 days out
            assertDoesNotThrow(() -> validator.validateTenderEditAllowed(t, normalUser));
        }

        @Test
        void editDeniedWithin24Hours() {
            Tender t = new Tender();
            // Clock is 2030-06-15T12:00. Start is 2030-06-16 00:00.
            // Lock time = start - 24h = 2030-06-15 00:00. Now (12:00) is after that.
            t.setStartDate(LocalDate.of(2030, 6, 16));
            assertThrows(ForbiddenException.class,
                    () -> validator.validateTenderEditAllowed(t, normalUser));
        }

        @Test
        void nullStartDate_skips() {
            Tender t = new Tender();
            assertDoesNotThrow(() -> validator.validateTenderEditAllowed(t, normalUser));
        }
    }

    @Nested
    @DisplayName("Auction edit-lock validation")
    class AuctionEditLock {

        @Test
        void adminBypassesEditLock() {
            Auction a = new Auction();
            a.setAuctionDate(LocalDate.of(2030, 6, 15));
            a.setAuctionTime(LocalTime.of(12, 30));
            assertDoesNotThrow(() -> validator.validateAuctionEditAllowed(a, adminUser));
        }

        @Test
        void editAllowedMoreThan1HourBefore() {
            Auction a = new Auction();
            a.setAuctionDate(LocalDate.of(2030, 6, 15));
            a.setAuctionTime(LocalTime.of(14, 0)); // start at 14:00, clock is 12:00
            assertDoesNotThrow(() -> validator.validateAuctionEditAllowed(a, normalUser));
        }

        @Test
        void editDeniedWithin1Hour() {
            Auction a = new Auction();
            a.setAuctionDate(LocalDate.of(2030, 6, 15));
            a.setAuctionTime(LocalTime.of(12, 30)); // 30 min from now
            assertThrows(ForbiddenException.class,
                    () -> validator.validateAuctionEditAllowed(a, normalUser));
        }

        @Test
        void nullDates_skips() {
            Auction a = new Auction();
            assertDoesNotThrow(() -> validator.validateAuctionEditAllowed(a, normalUser));
        }
    }

    @Nested
    @DisplayName("Delete validation")
    class DeleteValidation {

        @Test
        void tenderDelete_adminAlwaysAllowed() {
            Tender t = new Tender();
            t.setStartDate(LocalDate.of(2030, 6, 15));
            assertDoesNotThrow(() -> validator.validateTenderDeleteAllowed(t, adminUser));
        }

        @Test
        void auctionDelete_adminAlwaysAllowed() {
            Auction a = new Auction();
            a.setAuctionDate(LocalDate.of(2030, 6, 15));
            a.setAuctionTime(LocalTime.of(12, 30));
            assertDoesNotThrow(() -> validator.validateAuctionDeleteAllowed(a, adminUser));
        }
    }
}
