CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    mobile VARCHAR(20) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN','GOVERNMENT','COMPANY','USER')),
    organisation VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NULL,
    last_login TIMESTAMP NULL,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    lockout_until TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auctions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    photo VARCHAR(255),
    auction_date DATE,
    auction_time TIME,
    duration_minutes INTEGER,
    deadline TIMESTAMP NULL,
    starting_price DOUBLE,
    current_highest_bid DOUBLE DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT,
    winner_id BIGINT,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NULL,
    result_declared BOOLEAN NOT NULL DEFAULT FALSE,
    declaration_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_auction_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_auction_winner FOREIGN KEY (winner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auction_bids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    auction_id BIGINT NOT NULL,
    bidder_id BIGINT NOT NULL,
    bid_amount DOUBLE NOT NULL CHECK (bid_amount > 0),
    bid_time TIMESTAMP NULL,
    CONSTRAINT fk_auction_bid_auction FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
    CONSTRAINT fk_auction_bid_bidder FOREIGN KEY (bidder_id) REFERENCES users(id),
    CONSTRAINT uk_auction_bid_participant UNIQUE (auction_id, bidder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tenders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    category VARCHAR(255),
    start_date DATE,
    end_date DATE,
    deadline TIMESTAMP NULL,
    estimated_budget DOUBLE,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT,
    winner_id BIGINT,
    winning_bid DOUBLE,
    created_at TIMESTAMP NULL,
    result_declared BOOLEAN NOT NULL DEFAULT FALSE,
    result_declared_at TIMESTAMP NULL,
    declaration_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_tender_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_tender_winner FOREIGN KEY (winner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tender_bids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tender_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    bid_amount DOUBLE NOT NULL CHECK (bid_amount > 0),
    proposal VARCHAR(2000),
    document_path VARCHAR(255),
    submitted_at TIMESTAMP NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_tender_bid_tender FOREIGN KEY (tender_id) REFERENCES tenders(id) ON DELETE CASCADE,
    CONSTRAINT fk_tender_bid_company FOREIGN KEY (company_id) REFERENCES users(id),
    CONSTRAINT uk_tender_bid_participant UNIQUE (tender_id, company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE otp_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    otp VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity VARCHAR(255) NOT NULL,
    `timestamp` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata VARCHAR(2000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE email_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    notification_type VARCHAR(255) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    recipient_name VARCHAR(255),
    title VARCHAR(255),
    related_name VARCHAR(255),
    amount DOUBLE,
    entity_type VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NULL,
    sent_at TIMESTAMP NULL,
    last_error VARCHAR(1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE winner_declarations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    winner_id BIGINT,
    declared_at TIMESTAMP NULL,
    CONSTRAINT uk_winner_declaration_entity UNIQUE (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE scheduler_locks (
    lock_name VARCHAR(255) PRIMARY KEY,
    locked_until TIMESTAMP NULL,
    locked_by VARCHAR(255),
    version BIGINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_auction_status_deadline ON auctions(status, deadline, result_declared);
CREATE INDEX idx_tender_status_deadline ON tenders(status, deadline, result_declared);
CREATE INDEX idx_auction_bid_ranking ON auction_bids(auction_id, bid_amount DESC);
CREATE INDEX idx_tender_bid_ranking ON tender_bids(tender_id, bid_amount ASC);
CREATE INDEX idx_audit_timestamp ON audit_records(`timestamp` DESC);
CREATE INDEX idx_email_status_created ON email_notifications(status, created_at);