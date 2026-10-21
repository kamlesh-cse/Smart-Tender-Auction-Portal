-- BL-1 FIX: Convert all monetary columns from DOUBLE to DECIMAL(19,4).
-- DOUBLE cannot represent many decimal fractions exactly, causing potential
-- rounding errors in bid comparisons and winner determination.
-- DECIMAL(19,4) provides exact fixed-point arithmetic.

-- ── Auction monetary columns ─────────────────────────────────────────────────
ALTER TABLE auctions
    MODIFY COLUMN starting_price DECIMAL(19,4),
    MODIFY COLUMN current_highest_bid DECIMAL(19,4);

-- ── Auction bids ─────────────────────────────────────────────────────────────
ALTER TABLE auction_bids
    MODIFY COLUMN bid_amount DECIMAL(19,4) NOT NULL;

-- ── Tender monetary columns ──────────────────────────────────────────────────
ALTER TABLE tenders
    MODIFY COLUMN estimated_budget DECIMAL(19,4),
    MODIFY COLUMN winning_bid DECIMAL(19,4);

-- ── Tender bids ──────────────────────────────────────────────────────────────
ALTER TABLE tender_bids
    MODIFY COLUMN bid_amount DECIMAL(19,4) NOT NULL;

-- ── Email notification amount column ─────────────────────────────────────────
ALTER TABLE email_notifications
    MODIFY COLUMN amount DECIMAL(19,4);

-- Existing CHECK constraints on bid_amount > 0 remain valid for DECIMAL.
