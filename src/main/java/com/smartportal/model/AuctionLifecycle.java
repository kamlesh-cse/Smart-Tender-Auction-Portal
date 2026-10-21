package com.smartportal.model;

import java.time.LocalDateTime;

public final class AuctionLifecycle {

    public static final String UPCOMING = "UPCOMING";
    public static final String LIVE = "LIVE";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";

    private AuctionLifecycle() {
    }

    public static String status(String persistedStatus, LocalDateTime start, LocalDateTime end) {
        return status(persistedStatus, start, end, LocalDateTime.now());
    }

    public static String status(String persistedStatus, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (CANCELLED.equals(persistedStatus)) {
            return CANCELLED;
        }
        if (start != null && now.isBefore(start)) {
            return UPCOMING;
        }
        if (end != null && now.isAfter(end)) {
            return CLOSED;
        }
        // BUG-4 FIX: Incomplete auction (no dates) must NOT be treated as LIVE
        if (start == null || end == null) {
            return UPCOMING;
        }
        return LIVE;
    }

    public static boolean isUpcoming(String persistedStatus, LocalDateTime start, LocalDateTime end) {
        return UPCOMING.equals(status(persistedStatus, start, end));
    }

    public static boolean isLive(String persistedStatus, LocalDateTime start, LocalDateTime end) {
        return LIVE.equals(status(persistedStatus, start, end));
    }

    public static boolean isClosedForBidding(String persistedStatus, LocalDateTime start, LocalDateTime end) {
        String status = status(persistedStatus, start, end);
        return CLOSED.equals(status) || CANCELLED.equals(status);
    }
}
