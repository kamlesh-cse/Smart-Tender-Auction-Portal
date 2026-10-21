package com.smartportal.service;

import com.smartportal.exception.ResourceNotFoundException;
import com.smartportal.event.BidPlaced;
import com.smartportal.event.DomainEventPublisher;
import com.smartportal.event.EmailNotificationRequested;
import com.smartportal.model.*;
import com.smartportal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository bidRepository;
    private final BusinessRuleValidator businessRuleValidator;
    private final AuthorizationService authorizationService;
    private final BidBroadcastService bidBroadcastService;
    private final DomainEventPublisher events;
    private final AuditService auditService;
    private final PaymentService paymentService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public AuctionService(AuctionRepository auctionRepository,
            AuctionBidRepository bidRepository,
            BusinessRuleValidator businessRuleValidator,
            AuthorizationService authorizationService,
            BidBroadcastService bidBroadcastService,
            DomainEventPublisher events,
            AuditService auditService,
            PaymentService paymentService) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.businessRuleValidator = businessRuleValidator;
        this.authorizationService = authorizationService;
        this.bidBroadcastService = bidBroadcastService;
        this.events = events;
        this.auditService = auditService;
        this.paymentService = paymentService;
    }

    // ── Save Auction ───────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(cacheNames = "auctions", allEntries = true)
    public Auction save(Auction auction) {
        return saveInternal(auction, null, false, false);
    }

    /**
     * Save auction with optional admin bypass.
     * When isAdmin=true, the "already started" restriction is skipped.
     */
    @Transactional
    @CacheEvict(cacheNames = "auctions", allEntries = true)
    public Auction save(Auction auction, boolean isAdmin) {
        return saveInternal(auction, null, isAdmin, false);
    }

    @Transactional
    @CacheEvict(cacheNames = "auctions", allEntries = true)
    public Auction save(Auction auction, User actor) {
        return saveInternal(auction, actor, false, true);
    }

    private Auction saveInternal(Auction auction, User actor, boolean isAdmin, boolean enforceAuthorization) {
        boolean isNew = (auction.getId() == null);
        Auction existing = null;

        if (enforceAuthorization && isNew) {
            authorizationService.enforceCanCreateAuction(actor);
        }

        Auction target = auction;

        if (!isNew) {
            existing = auctionRepository.findById(auction.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Auction not found."));

            if (enforceAuthorization) {
                authorizationService.enforceCanEditOwnedResource(actor, existing.getCreatedBy(), "auction");
                businessRuleValidator.validateAuctionEditAllowed(existing, actor);
            } else if (!isAdmin) {
                businessRuleValidator.validateAuctionEditAllowed(existing, null);
            }

            existing.setTitle(auction.getTitle());
            existing.setDescription(auction.getDescription());
            existing.setPhoto(auction.getPhoto());
            existing.setAuctionDate(auction.getAuctionDate());
            existing.setAuctionTime(auction.getAuctionTime());
            existing.setDurationMinutes(auction.getDurationMinutes());
            existing.setStartingPrice(auction.getStartingPrice());
            existing.setStatus(auction.getStatus());
            if (auction.getCreatedBy() != null && isAdmin) {
                existing.setCreatedBy(auction.getCreatedBy());
            }
            target = existing;
        }

        businessRuleValidator.validateAuctionDuration(target);

        if (target.getAuctionDate() != null
                && target.getAuctionTime() != null
                && target.getDurationMinutes() != null) {

            LocalDateTime start = LocalDateTime.of(
                    target.getAuctionDate(),
                    target.getAuctionTime());
            target.setDeadline(start.plusMinutes(target.getDurationMinutes()));
        }

        Auction saved = auctionRepository.save(target);
        auditService.record(actor != null ? String.valueOf(actor.getId()) : (isAdmin ? "ADMIN" : "SYSTEM"),
                isNew ? "CREATE" : "EDIT", "AUCTION:" + saved.getId(), "title=" + saved.getTitle());
        if (isNew && saved.getCreatedBy() != null && saved.getCreatedBy().getEmail() != null) {
            String deadline = saved.getDeadline() != null ? saved.getDeadline().format(DT_FMT) : "Not set";
            events.publishAfterCommit(email("auction:" + saved.getId() + ":created", "AUCTION_CREATED",
                    saved.getCreatedBy().getEmail(), saved.getCreatedBy().getName(),
                    saved.getTitle(), deadline, null, "Auction"));
        }

        return saved;
    }

    // ── Basic Fetch Methods ────────────────────────────────────────────────────
    public List<Auction> getAll() {
        return auctionRepository.findAll();
    }

    public Page<Auction> getAll(Pageable pageable) {
        return auctionRepository.findAll(pageable);
    }

    public List<Auction> getOpen() {
        // BUG-5 FIX: Pre-filter by status in DB to avoid loading entire auctions table
        return auctionRepository.findByStatus("OPEN").stream()
                .filter(Auction::isLive)
                .toList();
    }

    public Page<Auction> getOpen(Pageable pageable) {
        List<Auction> live = getOpen();
        int start = (int) Math.min(pageable.getOffset(), live.size());
        int end = Math.min(start + pageable.getPageSize(), live.size());
        return new PageImpl<>(live.subList(start, end), pageable, live.size());
    }

    public List<Auction> getClosed() {
        // BUG-5 FIX: Pre-filter by status in DB
        return auctionRepository.findByStatus("CLOSED").stream()
                .filter(a -> "CLOSED".equals(a.getLifecycleStatus()))
                .toList();
    }

    @Cacheable(cacheNames = "auctions", key = "#id")
    public Auction getById(Long id) {
        return auctionRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = "auctions", key = "#id")
    public String deleteIfNotStarted(Long id) {
        Auction auction = auctionRepository.findById(id).orElse(null);
        if (auction == null) return "NOT_FOUND";
        if (auction.isEditLocked()) return "EDIT_LOCKED";
        auctionRepository.deleteById(id);
        return "SUCCESS";
    }

    @CacheEvict(cacheNames = "auctions", key = "#id")
    public String deleteIfAllowed(Long id, User actor) {
        Auction auction = auctionRepository.findById(id).orElse(null);
        if (auction == null) return "NOT_FOUND";
        authorizationService.enforceCanEditOwnedResource(actor, auction.getCreatedBy(), "auction");
        businessRuleValidator.validateAuctionDeleteAllowed(auction, actor);
        auctionRepository.deleteById(id);
        return "SUCCESS";
    }

    public void validateEditable(Long id, User actor) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found."));
        authorizationService.enforceCanEditOwnedResource(actor, auction.getCreatedBy(), "auction");
        businessRuleValidator.validateAuctionEditAllowed(auction, actor);
    }

    /**
     * Admin-only: delete auction unconditionally (even if already started).
     */
    @Transactional
    @CacheEvict(cacheNames = "auctions", key = "#id")
    public String adminDelete(Long id) {
        Auction auction = auctionRepository.findById(id).orElse(null);
        if (auction == null) return "NOT_FOUND";
        auctionRepository.deleteById(id);
        return "SUCCESS";
    }

    public List<Auction> getByCreatedBy(User user) {
        return auctionRepository.findByCreatedBy(user);
    }

    public List<Auction> getOpenByUser(User user) {
        return auctionRepository.findByCreatedBy(user).stream()
                .filter(Auction::isLive)
                .toList();
    }

    public List<Auction> getClosedByUser(User user) {
        return auctionRepository.findByCreatedByAndStatus(user, "CLOSED");
    }

    // ── Place Bid ──────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(cacheNames = "auctions", key = "#auctionId")
    public String placeBid(Long auctionId, User bidder, Double amount) {

        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null)
            throw new ResourceNotFoundException("Auction not found.");

        authorizationService.enforceCanParticipateInAuction(bidder);

        if (auction.getCreatedBy() != null
                && auction.getCreatedBy().getId().equals(bidder.getId()))
            return "OWN_AUCTION";

        String lifecycleStatus = AuctionLifecycle.status(
                auction.getStatus(), auction.getStartDateTime(), auction.getDeadline());

        if (AuctionLifecycle.CANCELLED.equals(lifecycleStatus))
            return "ALREADY_CLOSED";

        if (AuctionLifecycle.UPCOMING.equals(lifecycleStatus))
            return "NOT_STARTED";

        if (!AuctionLifecycle.LIVE.equals(lifecycleStatus))
            return "ALREADY_CLOSED";

        // C-3 FIX: Prevent same user from bidding twice on the same auction
        if (bidRepository.existsByAuctionIdAndBidderId(auctionId, bidder.getId()))
            return "ALREADY_BID";

        // EMD Gate: FR-5 requires EMD to be paid before bidding
        if (!paymentService.hasPaidEmdForAuction(auctionId, bidder.getId()))
            return "EMD_NOT_PAID";

        double currentHighest = auction.getCurrentHighestBid() != null ? auction.getCurrentHighestBid() : 0.0;
        double startingPrice = auction.getStartingPrice() != null ? auction.getStartingPrice() : 0.0;
        double minimum = currentHighest > 0 ? currentHighest : startingPrice;

        if (amount <= minimum)
            return "BID_TOO_LOW";

        try {
            AuctionBid bid = new AuctionBid();
            bid.setAuction(auction);
            bid.setBidder(bidder);
            bid.setBidAmount(amount);
            bid = bidRepository.saveAndFlush(bid);

            auction.setCurrentHighestBid(amount);
            auctionRepository.saveAndFlush(auction);
            events.publishAfterCommit(new BidPlaced("AUCTION", auctionId,
                    bidBroadcastService.createAuctionPayload(bid), LocalDateTime.now()));
            auditService.record(String.valueOf(bidder.getId()), "BID", "AUCTION:" + auctionId,
                    "bidId=" + bid.getId() + ",amount=" + amount);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent bid detected on auction {}: {}", auctionId, e.getMessage());
            return "CONCURRENT_BID";
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate auction bid detected on auction {} for bidder {}", auctionId, bidder.getId());
            return "ALREADY_BID";
        }

        // Email bidder — confirmation
        try {
            events.publishAfterCommit(email("auction-bid:" + auctionId + ":" + bidder.getId() + ":bidder",
                    "AUCTION_BID", bidder.getEmail(), bidder.getName(), auction.getTitle(),
                    null, amount, "Auction"));
        } catch (Exception e) {
            log.warn("Failed to send bid-placed email to {}: {}", bidder.getEmail(), e.getMessage());
        }

        // Email auction creator — alert
        try {
            if (auction.getCreatedBy() != null && auction.getCreatedBy().getEmail() != null) {
                events.publishAfterCommit(email("auction-bid:" + auctionId + ":" + bidder.getId() + ":creator",
                        "AUCTION_BID_ALERT", auction.getCreatedBy().getEmail(),
                        auction.getCreatedBy().getName(), auction.getTitle(), bidder.getName(),
                        amount, "Auction"));
            }
        } catch (Exception e) {
            log.warn("Failed to send bid-alert email to creator: {}", e.getMessage());
        }

        return "SUCCESS";
    }

    public List<AuctionBid> getBidsForAuction(Long auctionId) {
        return bidRepository.findByAuctionIdOrderByBidAmountDesc(auctionId);
    }

    public List<AuctionBid> getBidsByUser(User user) {
        return bidRepository.findByBidderId(user.getId());
    }

    private EmailNotificationRequested email(String key, String type, String to, String name,
            String title, String relatedName, Double amount, String entityType) {
        return new EmailNotificationRequested(key, type, to, name, title, relatedName,
                amount, entityType, LocalDateTime.now());
    }
}
