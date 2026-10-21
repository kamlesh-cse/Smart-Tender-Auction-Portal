package com.smartportal.service;

import com.smartportal.dto.BidMessage;
import com.smartportal.event.AuctionClosed;
import com.smartportal.event.BidPlaced;
import com.smartportal.event.TenderClosed;
import com.smartportal.model.Auction;
import com.smartportal.model.AuctionBid;
import com.smartportal.model.Tender;
import com.smartportal.model.TenderBid;
import com.smartportal.repository.AuctionBidRepository;
import com.smartportal.repository.TenderBidRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.micrometer.core.instrument.Metrics;

@Service
public class BidBroadcastService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd MMM");

    private final SimpMessagingTemplate messagingTemplate;
    private final AuctionBidRepository auctionBidRepository;
    private final TenderBidRepository tenderBidRepository;

    public BidBroadcastService(SimpMessagingTemplate messagingTemplate,
            AuctionBidRepository auctionBidRepository,
            TenderBidRepository tenderBidRepository) {
        this.messagingTemplate = messagingTemplate;
        this.auctionBidRepository = auctionBidRepository;
        this.tenderBidRepository = tenderBidRepository;
    }

    public void broadcastAuctionBid(AuctionBid bid) {
        afterCommit(() -> {
            List<AuctionBid> bids =
                    auctionBidRepository.findByAuctionIdOrderByBidAmountDesc(bid.getAuction().getId());
            messagingTemplate.convertAndSend(
                    "/topic/auction/" + bid.getAuction().getId(), auctionMessage(bid, bids));
        });
    }

    public void broadcast(BidPlaced event) {
        String destination = "/topic/" + event.entityType().toLowerCase() + "/" + event.entityId();
        messagingTemplate.convertAndSend(destination, event.payload());
        Metrics.counter("smartportal.websocket.broadcasts", "event", "bid").increment();
    }

    public void broadcast(AuctionClosed event) {
        messagingTemplate.convertAndSend(
                "/topic/auction/" + event.auctionId(), BidMessage.closed(event.winner()));
        Metrics.counter("smartportal.websocket.broadcasts", "event", "auction_closed").increment();
    }

    public void broadcast(TenderClosed event) {
        messagingTemplate.convertAndSend(
                "/topic/tender/" + event.tenderId(), BidMessage.closed(event.winner()));
        Metrics.counter("smartportal.websocket.broadcasts", "event", "tender_closed").increment();
    }

    public void broadcastTenderBid(TenderBid bid) {
        afterCommit(() -> {
            List<TenderBid> bids =
                    tenderBidRepository.findByTenderIdOrderByBidAmountAsc(bid.getTender().getId());
            messagingTemplate.convertAndSend(
                    "/topic/tender/" + bid.getTender().getId(), tenderMessage(bid, bids));
        });
    }

    public void broadcastAuctionClosed(Auction auction) {
        String winner = auction.getWinner() != null ? auction.getWinner().getName() : "";
        afterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/auction/" + auction.getId(), BidMessage.closed(winner)));
    }

    public void broadcastTenderClosed(Tender tender) {
        String winner = tender.getWinner() != null ? tender.getWinner().getName() : "";
        afterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/tender/" + tender.getId(), BidMessage.closed(winner)));
    }

    private BidMessage auctionMessage(AuctionBid bid, List<AuctionBid> bids) {
        BidMessage message = baseMessage(
                bid.getId(), bid.getBidAmount(), bid.getBidder().getName(), bid.getBidTime());
        message.setRank(String.valueOf(rankAuctionBid(bid, bids)));
        message.setTotalParticipants(String.valueOf(
                bids.stream().map(item -> item.getBidder().getId()).distinct().count()));
        message.setAuctionStatus(bid.getAuction().getLifecycleStatus());
        return message;
    }

    public BidMessage createAuctionPayload(AuctionBid bid) {
        return auctionMessage(bid,
                auctionBidRepository.findByAuctionIdOrderByBidAmountDesc(bid.getAuction().getId()));
    }

    private BidMessage tenderMessage(TenderBid bid, List<TenderBid> bids) {
        BidMessage message = baseMessage(
                bid.getId(), bid.getBidAmount(), bid.getCompany().getName(), bid.getSubmittedAt());
        message.setRank(String.valueOf(rankTenderBid(bid, bids)));
        message.setTotalParticipants(String.valueOf(
                bids.stream().map(item -> item.getCompany().getId()).distinct().count()));
        message.setAuctionStatus(bid.getTender().getStatus());
        return message;
    }

    public BidMessage createTenderPayload(TenderBid bid) {
        return tenderMessage(bid,
                tenderBidRepository.findByTenderIdOrderByBidAmountAsc(bid.getTender().getId()));
    }

    private BidMessage baseMessage(Long id, Double amount, String bidderName, LocalDateTime time) {
        BidMessage message = new BidMessage();
        message.setEvent("BID_PLACED");
        message.setBidId(String.valueOf(id));
        message.setAmount(amount != null ? String.format("%.2f", amount) : "0.00");
        message.setBidderName(bidderName);
        message.setTime((time != null ? time : LocalDateTime.now()).format(TIME_FORMAT));
        return message;
    }

    private int rankAuctionBid(AuctionBid target, List<AuctionBid> bids) {
        for (int i = 0; i < bids.size(); i++) {
            if (target.getId() != null && target.getId().equals(bids.get(i).getId())) return i + 1;
        }
        return bids.size();
    }

    private int rankTenderBid(TenderBid target, List<TenderBid> bids) {
        for (int i = 0; i < bids.size(); i++) {
            if (target.getId() != null && target.getId().equals(bids.get(i).getId())) return i + 1;
        }
        return bids.size();
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
