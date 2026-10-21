-- V5: Add missing tables for EMD Payments, Tender Documents, and Notifications

-- 1. EMD Payment (Earnest Money Deposit)
-- Vendors must pay EMD before bidding. FK points to Tender or Auction, and the Vendor.
CREATE TABLE emd_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tender_id BIGINT,
    auction_id BIGINT,
    vendor_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    transaction_id VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_emd_tender FOREIGN KEY (tender_id) REFERENCES tenders(id),
    CONSTRAINT fk_emd_auction FOREIGN KEY (auction_id) REFERENCES auctions(id),
    CONSTRAINT fk_emd_vendor FOREIGN KEY (vendor_id) REFERENCES users(id),
    CONSTRAINT emd_target_check CHECK (
        (tender_id IS NOT NULL AND auction_id IS NULL) OR
        (tender_id IS NULL AND auction_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Tender Documents
-- Multiple documents can be uploaded for a tender by its creator
CREATE TABLE tender_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tender_id BIGINT NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_path VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tender_doc_tender FOREIGN KEY (tender_id) REFERENCES tenders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Notifications
-- In-app notifications for users (e.g. outbid alerts, tender updates)
CREATE TABLE notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Audit Record updates
ALTER TABLE audit_records ADD COLUMN ip_address VARCHAR(45);