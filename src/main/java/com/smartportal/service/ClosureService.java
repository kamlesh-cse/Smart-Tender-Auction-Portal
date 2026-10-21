package com.smartportal.service;

import com.smartportal.event.AuctionClosed;
import com.smartportal.event.DomainEventPublisher;
import com.smartportal.event.EmailNotificationRequested;
import com.smartportal.event.TenderClosed;
import com.smartportal.event.WinnerDeclared;
import com.smartportal.model.Auction;
import com.smartportal.model.AuctionBid;
import com.smartportal.model.Tender;
import com.smartportal.model.TenderBid;
import com.smartportal.model.WinnerDeclaration;
import com.smartportal.repository.AuctionBidRepository;
import com.smartportal.repository.AuctionRepository;
import com.smartportal.repository.TenderBidRepository;
import com.smartportal.repository.TenderRepository;
import com.smartportal.repository.WinnerDeclarationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClosureService {
    private final AuctionRepository auctionRepository;
    private final TenderRepository tenderRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final TenderBidRepository tenderBidRepository;
    private final WinnerDeclarationRepository declarationRepository;
    private final DomainEventPublisher events;
    private final AuditService auditService;

    public ClosureService(AuctionRepository auctionRepository, TenderRepository tenderRepository,
            AuctionBidRepository auctionBidRepository, TenderBidRepository tenderBidRepository,
            WinnerDeclarationRepository declarationRepository, DomainEventPublisher events,
            AuditService auditService) {
        this.auctionRepository = auctionRepository;
        this.tenderRepository = tenderRepository;
        this.auctionBidRepository = auctionBidRepository;
        this.tenderBidRepository = tenderBidRepository;
        this.declarationRepository = declarationRepository;
        this.events = events;
        this.auditService = auditService;
    }

    // BUG-6 FIX: outer method is NOT @Transactional — each auction is closed in its own transaction
    @Retryable(retryFor = RuntimeException.class, maxAttemptsExpression = "${app.retry.close.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.close.delay-ms:500}", multiplier = 2))
    public int closeExpiredAuctions() {
        List<Auction> expired = auctionRepository.findExpiredActiveAuctions(LocalDateTime.now());
        int closed = 0;
        for (Auction auction : expired) {
            try {
                if (closeSingleAuction(auction.getId())) closed++;
            } catch (Exception e) {
                // BUG-6 FIX: one bad auction does not prevent others from closing
                auditService.record("SYSTEM", "CLOSE_ERROR", "AUCTION:" + auction.getId(), e.getMessage());
            }
        }
        return closed;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean closeSingleAuction(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) return false;
        if (auction.isResultDeclared() || "DECLARED".equals(auction.getDeclarationStatus())) return false;
        auction.setDeclarationStatus("PROCESSING");
        Optional<AuctionBid> topBid =
                auctionBidRepository.findTopByAuctionIdOrderByBidAmountDesc(auction.getId());
        auction.setStatus("CLOSED");
        if (topBid.isPresent()) {
            AuctionBid winningBid = topBid.get();
            auction.setWinner(winningBid.getBidder());
            auction.setCurrentHighestBid(winningBid.getBidAmount());
            saveDeclaration("AUCTION", auction.getId(), winningBid.getBidder().getId());
            publishWinnerEmails(auction, winningBid);
            events.publishAfterCommit(new WinnerDeclared("AUCTION", auction.getId(),
                    winningBid.getBidder().getId(), winningBid.getBidder().getName(),
                    winningBid.getBidAmount(), LocalDateTime.now()));
            auditService.record("SYSTEM", "WINNER", "AUCTION:" + auction.getId(),
                    "winnerId=" + winningBid.getBidder().getId());
        }
        auction.setResultDeclared(true);
        auction.setDeclarationStatus("DECLARED");
        auctionRepository.saveAndFlush(auction);
        auditService.record("SYSTEM", "CLOSE", "AUCTION:" + auction.getId(), "status=CLOSED");
        events.publishAfterCommit(new AuctionClosed(auction.getId(),
                auction.getWinner() != null ? auction.getWinner().getName() : "", LocalDateTime.now()));
        return true;
    }

    @Transactional
    @Retryable(retryFor = RuntimeException.class, maxAttemptsExpression = "${app.retry.close.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.close.delay-ms:500}", multiplier = 2))
    public int closeExpiredTenders() {
        List<Tender> expired = tenderRepository
                .findByStatusAndDeadlineBeforeAndResultDeclaredFalse("OPEN", LocalDateTime.now());
        int closed = 0;
        for (Tender tender : expired) {
            if (tender.isResultDeclared() || "DECLARED".equals(tender.getDeclarationStatus())) continue;
            tender.setDeclarationStatus("PROCESSING");
            Optional<TenderBid> lowestBid =
                    tenderBidRepository.findTopByTenderIdOrderByBidAmountAsc(tender.getId());
            tender.setStatus("CLOSED");
            if (lowestBid.isPresent()) {
                TenderBid winningBid = lowestBid.get();
                tender.setWinner(winningBid.getCompany());
                tender.setWinningBid(winningBid.getBidAmount());
                winningBid.setStatus("ACCEPTED");
                tenderBidRepository.save(winningBid);
                saveDeclaration("TENDER", tender.getId(), winningBid.getCompany().getId());
                publishWinnerEmails(tender, winningBid);
                events.publishAfterCommit(new WinnerDeclared("TENDER", tender.getId(),
                        winningBid.getCompany().getId(), winningBid.getCompany().getName(),
                        winningBid.getBidAmount(), LocalDateTime.now()));
                auditService.record("SYSTEM", "WINNER", "TENDER:" + tender.getId(),
                        "winnerId=" + winningBid.getCompany().getId());
            }
            tender.setResultDeclared(true);
            tender.setResultDeclaredAt(LocalDateTime.now());
            tender.setDeclarationStatus("DECLARED");
            tenderRepository.saveAndFlush(tender);
            auditService.record("SYSTEM", "CLOSE", "TENDER:" + tender.getId(), "status=CLOSED");
            events.publishAfterCommit(new TenderClosed(tender.getId(),
                    tender.getWinner() != null ? tender.getWinner().getName() : "", LocalDateTime.now()));
            closed++;
        }
        return closed;
    }

    private void saveDeclaration(String type, Long entityId, Long winnerId) {
        if (declarationRepository.existsByEntityTypeAndEntityId(type, entityId)) return;
        WinnerDeclaration declaration = new WinnerDeclaration();
        declaration.setEntityType(type);
        declaration.setEntityId(entityId);
        declaration.setWinnerId(winnerId);
        declaration.setDeclaredAt(LocalDateTime.now());
        declarationRepository.saveAndFlush(declaration);
    }

    private void publishWinnerEmails(Auction auction, AuctionBid winningBid) {
        events.publishAfterCommit(email("auction:" + auction.getId() + ":winner",
                "AUCTION_WINNER", winningBid.getBidder().getEmail(), winningBid.getBidder().getName(),
                auction.getTitle(), null, winningBid.getBidAmount(), "Auction"));
        if (auction.getCreatedBy() != null) {
            events.publishAfterCommit(email("auction:" + auction.getId() + ":creator-result",
                    "RESULT_CREATOR", auction.getCreatedBy().getEmail(), auction.getCreatedBy().getName(),
                    auction.getTitle(), winningBid.getBidder().getName(), winningBid.getBidAmount(), "Auction"));
        }
        for (AuctionBid bid : auctionBidRepository.findByAuctionIdOrderByBidAmountDesc(auction.getId())) {
            if (!bid.getBidder().getId().equals(winningBid.getBidder().getId())) {
                events.publishAfterCommit(email("auction:" + auction.getId() + ":loser:" + bid.getBidder().getId(),
                        "LOSER", bid.getBidder().getEmail(), bid.getBidder().getName(),
                        auction.getTitle(), null, null, "Auction"));
            }
        }
    }

    private void publishWinnerEmails(Tender tender, TenderBid winningBid) {
        events.publishAfterCommit(email("tender:" + tender.getId() + ":winner",
                "TENDER_WINNER", winningBid.getCompany().getEmail(), winningBid.getCompany().getName(),
                tender.getTitle(), null, winningBid.getBidAmount(), "Tender"));
        if (tender.getCreatedBy() != null) {
            events.publishAfterCommit(email("tender:" + tender.getId() + ":creator-result",
                    "RESULT_CREATOR", tender.getCreatedBy().getEmail(), tender.getCreatedBy().getName(),
                    tender.getTitle(), winningBid.getCompany().getName(), winningBid.getBidAmount(), "Tender"));
        }
        for (TenderBid bid : tenderBidRepository.findByTenderIdOrderByBidAmountAsc(tender.getId())) {
            if (!bid.getCompany().getId().equals(winningBid.getCompany().getId())) {
                bid.setStatus("REJECTED");
                tenderBidRepository.save(bid);
                events.publishAfterCommit(email("tender:" + tender.getId() + ":loser:" + bid.getCompany().getId(),
                        "LOSER", bid.getCompany().getEmail(), bid.getCompany().getName(),
                        tender.getTitle(), null, null, "Tender"));
            }
        }
    }

    private EmailNotificationRequested email(String key, String type, String to, String name,
            String title, String relatedName, Double amount, String entityType) {
        return new EmailNotificationRequested(key, type, to, name, title, relatedName,
                amount, entityType, LocalDateTime.now());
    }
}
