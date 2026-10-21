package com.smartportal.service;

import com.smartportal.dto.BidMessage;
import com.smartportal.model.Auction;
import com.smartportal.model.AuctionBid;
import com.smartportal.model.User;
import com.smartportal.repository.AuctionBidRepository;
import com.smartportal.repository.TenderBidRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketIntegrationTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private AuctionBidRepository auctionBidRepository;
    @Mock private TenderBidRepository tenderBidRepository;

    @Test
    void auctionBidIsPublishedToAuctionTopicWithoutEmail() {
        BidBroadcastService service =
                new BidBroadcastService(messagingTemplate, auctionBidRepository, tenderBidRepository);
        User bidder = new User();
        bidder.setId(7L);
        bidder.setName("Acme");
        bidder.setEmail("must-not-be-broadcast@example.com");
        Auction auction = new Auction();
        auction.setId(42L);
        auction.setStatus("OPEN");
        AuctionBid bid = new AuctionBid();
        bid.setId(99L);
        bid.setAuction(auction);
        bid.setBidder(bidder);
        bid.setBidAmount(12500.0);
        bid.setBidTime(LocalDateTime.of(2026, 6, 14, 18, 30));
        when(auctionBidRepository.findByAuctionIdOrderByBidAmountDesc(42L))
                .thenReturn(List.of(bid));

        service.broadcastAuctionBid(bid);

        ArgumentCaptor<BidMessage> payload = ArgumentCaptor.forClass(BidMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/auction/42"), payload.capture());
        assertEquals("99", payload.getValue().getBidId());
        assertEquals("Acme", payload.getValue().getBidderName());
        assertEquals("1", payload.getValue().getRank());
        assertEquals("1", payload.getValue().getTotalParticipants());
        assertNull(payload.getValue().getWinner());
    }

    @Test
    void closedEventIsPublishedImmediatelyWithWinner() {
        BidBroadcastService service =
                new BidBroadcastService(messagingTemplate, auctionBidRepository, tenderBidRepository);
        User winner = new User();
        winner.setName("Winning Company");
        Auction auction = new Auction();
        auction.setId(42L);
        auction.setWinner(winner);

        service.broadcastAuctionClosed(auction);

        ArgumentCaptor<BidMessage> payload = ArgumentCaptor.forClass(BidMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/auction/42"), payload.capture());
        assertEquals("CLOSED", payload.getValue().getEvent());
        assertEquals("Winning Company", payload.getValue().getWinner());
    }
}
