package com.smartportal;

import com.smartportal.model.Auction;
import com.smartportal.model.User;
import com.smartportal.repository.AuctionBidRepository;
import com.smartportal.repository.AuctionRepository;
import com.smartportal.repository.UserRepository;
import com.smartportal.service.AuctionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.smartportal.service.PaymentService;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
class ConcurrentBidTest {

    @Autowired AuctionService auctionService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired AuctionBidRepository bidRepository;
    @Autowired UserRepository userRepository;
    @MockBean PaymentService paymentService;

    @Test
    @DisplayName("Concurrent bids: only valid bids succeed, no data corruption")
    void concurrentBids_noDuplicatesOrCorruption() throws InterruptedException {
        // Setup: create auction creator
        User creator = new User();
        creator.setName("Creator");
        creator.setEmail("creator-concurrent-" + System.currentTimeMillis() + "@test.com");
        creator.setMobile("9000000001");
        creator.setPassword("Password1234!");
        creator.setRole("COMPANY");
        creator.setActive(true);
        creator = userRepository.save(creator);

        // Setup: create auction
        Auction auction = new Auction();
        auction.setTitle("Concurrent Test Auction");
        auction.setStartingPrice(100.0);
        auction.setCurrentHighestBid(0.0);
        auction.setStatus("OPEN");
        auction.setCreatedBy(creator);
        auction.setAuctionDate(LocalDate.now().minusDays(1));
        auction.setAuctionTime(LocalTime.NOON);
        auction.setDurationMinutes(1440);
        auction.setDeadline(LocalDateTime.now().plusHours(24));
        auction = auctionRepository.save(auction);
        final Long auctionId = auction.getId();

        // Create 5 bidders
        List<User> bidders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User bidder = new User();
            bidder.setName("Bidder" + i);
            bidder.setEmail("bidder-concurrent-" + i + "-" + System.currentTimeMillis() + "@test.com");
            bidder.setMobile("900000" + String.format("%04d", i + 10));
            bidder.setPassword("Password1234!");
            bidder.setRole("COMPANY");
            bidder.setActive(true);
            bidders.add(userRepository.save(bidder));
        }

        when(paymentService.hasPaidEmdForAuction(anyLong(), anyLong())).thenReturn(true);

        // Execute: 5 threads bid concurrently with different amounts
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final User bidder = bidders.get(i);
            final double amount = 200.0 + (i * 100); // 200, 300, 400, 500, 600
            futures.add(executor.submit(() -> auctionService.placeBid(auctionId, bidder, amount)));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        // Collect results
        long successCount = 0;
        for (Future<String> future : futures) {
            try {
                String result = future.get();
                if ("SUCCESS".equals(result)) successCount++;
            } catch (ExecutionException e) {
                // Some bids may fail due to optimistic locking — that's expected
            }
        }

        // At least one bid should succeed
        assertTrue(successCount >= 1, "At least one concurrent bid should succeed");

        // Verify no duplicate bids per bidder
        for (User bidder : bidders) {
            long bidCount = bidRepository.findAll().stream()
                    .filter(b -> b.getAuction().getId().equals(auctionId)
                            && b.getBidder().getId().equals(bidder.getId()))
                    .count();
            assertTrue(bidCount <= 1,
                    "Bidder " + bidder.getName() + " should have at most 1 bid, found " + bidCount);
        }

        // Verify auction's highest bid is consistent
        Auction finalAuction = auctionRepository.findById(auctionId).orElseThrow();
        if (finalAuction.getCurrentHighestBid() > 0) {
            assertTrue(finalAuction.getCurrentHighestBid() >= 200.0);
        }
    }
}
