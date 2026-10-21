package com.smartportal.model;

/**
 * L-4 FIX: Replaces raw "OPEN"/"CLOSED"/"CANCELLED" strings with a type-safe enum.
 * Use @Enumerated(EnumType.STRING) on the Auction.status field.
 */
public enum AuctionStatus {
    UPCOMING,
    LIVE,
    CLOSED,
    CANCELLED
}
