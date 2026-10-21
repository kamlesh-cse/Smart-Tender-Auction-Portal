ALTER TABLE auctions
    ADD CONSTRAINT chk_auction_status
        CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'));
ALTER TABLE auctions
    ADD CONSTRAINT chk_auction_duration
        CHECK (duration_minutes IS NULL OR duration_minutes >= 30);
ALTER TABLE auctions
    ADD CONSTRAINT chk_auction_starting_price
        CHECK (starting_price IS NULL OR starting_price >= 0);
ALTER TABLE auctions
    ADD CONSTRAINT chk_auction_current_bid
        CHECK (current_highest_bid IS NULL OR current_highest_bid >= 0);

ALTER TABLE tenders
    ADD CONSTRAINT chk_tender_status
        CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'));
ALTER TABLE tenders
    ADD CONSTRAINT chk_tender_budget
        CHECK (estimated_budget IS NULL OR estimated_budget >= 0);
ALTER TABLE tenders
    ADD CONSTRAINT chk_tender_dates
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date);

ALTER TABLE tender_bids
    ADD CONSTRAINT chk_tender_bid_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'));

CREATE INDEX idx_users_role_active ON users(role, active);
CREATE INDEX idx_auctions_created_by ON auctions(created_by);
CREATE INDEX idx_tenders_created_by ON tenders(created_by);
CREATE INDEX idx_auction_bids_bidder ON auction_bids(bidder_id);
CREATE INDEX idx_tender_bids_company ON tender_bids(company_id);
CREATE INDEX idx_otp_expires_at ON otp_tokens(expires_at);
