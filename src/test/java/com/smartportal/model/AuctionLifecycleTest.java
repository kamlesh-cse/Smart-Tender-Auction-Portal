package com.smartportal.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AuctionLifecycleTest {

    @Test
    @DisplayName("CANCELLED status always returns CANCELLED regardless of dates")
    void cancelled_overridesAllDates() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        LocalDateTime future = LocalDateTime.now().plusDays(1);

        assertEquals("CANCELLED", AuctionLifecycle.status("CANCELLED", past, future));
        assertEquals("CANCELLED", AuctionLifecycle.status("CANCELLED", null, null));
        assertEquals("CANCELLED", AuctionLifecycle.status("CANCELLED", future, past));
    }

    @Test
    @DisplayName("Future start → UPCOMING")
    void futureStart_isUpcoming() {
        LocalDateTime future = LocalDateTime.now().plusHours(2);
        LocalDateTime farFuture = LocalDateTime.now().plusHours(4);

        assertEquals("UPCOMING", AuctionLifecycle.status("OPEN", future, farFuture));
        assertTrue(AuctionLifecycle.isUpcoming("OPEN", future, farFuture));
        assertFalse(AuctionLifecycle.isLive("OPEN", future, farFuture));
        assertFalse(AuctionLifecycle.isClosedForBidding("OPEN", future, farFuture));
    }

    @Test
    @DisplayName("Started with future deadline → LIVE")
    void startedAndNotExpired_isLive() {
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        LocalDateTime future = LocalDateTime.now().plusHours(1);

        assertEquals("LIVE", AuctionLifecycle.status("OPEN", past, future));
        assertTrue(AuctionLifecycle.isLive("OPEN", past, future));
        assertFalse(AuctionLifecycle.isUpcoming("OPEN", past, future));
        assertFalse(AuctionLifecycle.isClosedForBidding("OPEN", past, future));
    }

    @Test
    @DisplayName("Past deadline → CLOSED")
    void pastDeadline_isClosed() {
        LocalDateTime past = LocalDateTime.now().minusHours(3);
        LocalDateTime recentPast = LocalDateTime.now().minusMinutes(1);

        assertEquals("CLOSED", AuctionLifecycle.status("OPEN", past, recentPast));
        assertTrue(AuctionLifecycle.isClosedForBidding("OPEN", past, recentPast));
        assertFalse(AuctionLifecycle.isLive("OPEN", past, recentPast));
    }

    @Test
    @DisplayName("BUG-4: null dates → UPCOMING, not LIVE")
    void nullDates_returnsUpcoming() {
        assertEquals("UPCOMING", AuctionLifecycle.status("OPEN", null, null));
        assertEquals("UPCOMING", AuctionLifecycle.status("OPEN", null, LocalDateTime.now().plusHours(1)));
        assertEquals("UPCOMING", AuctionLifecycle.status("OPEN", LocalDateTime.now().minusHours(1), null));
    }

    @Test
    @DisplayName("isClosedForBidding includes CANCELLED")
    void closedForBidding_includesCancelled() {
        LocalDateTime past = LocalDateTime.now().minusHours(2);
        assertTrue(AuctionLifecycle.isClosedForBidding("CANCELLED", past, past));
    }

    @Test
    @DisplayName("4-arg status method respects injected 'now' time")
    void fourArgStatus_usesInjectedNow() {
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2030, 1, 1, 12, 0);

        LocalDateTime beforeStart = LocalDateTime.of(2030, 1, 1, 9, 0);
        LocalDateTime during      = LocalDateTime.of(2030, 1, 1, 11, 0);
        LocalDateTime afterEnd    = LocalDateTime.of(2030, 1, 1, 13, 0);

        assertEquals("UPCOMING", AuctionLifecycle.status("OPEN", start, end, beforeStart));
        assertEquals("LIVE",     AuctionLifecycle.status("OPEN", start, end, during));
        assertEquals("CLOSED",   AuctionLifecycle.status("OPEN", start, end, afterEnd));
    }

    @Test
    @DisplayName("Exactly at start time → LIVE (not before start)")
    void atExactStartTime_isLive() {
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2030, 1, 1, 12, 0);

        assertEquals("LIVE", AuctionLifecycle.status("OPEN", start, end, start));
    }

    @Test
    @DisplayName("Exactly at end time → CLOSED (isAfter end is false, so LIVE)")
    void atExactEndTime_isLive() {
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2030, 1, 1, 12, 0);

        // At exactly the end time, now.isAfter(end) is FALSE, so status is LIVE
        assertEquals("LIVE", AuctionLifecycle.status("OPEN", start, end, end));
    }
}
