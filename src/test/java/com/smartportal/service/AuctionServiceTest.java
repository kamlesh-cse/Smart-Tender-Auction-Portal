package com.smartportal.service;

import com.smartportal.model.*;
import com.smartportal.exception.ResourceNotFoundException;
import com.smartportal.repository.AuctionBidRepository;
import com.smartportal.repository.AuctionRepository;
import com.smartportal.event.DomainEventPublisher;
import com.smartportal.event.EmailNotificationRequested;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock private AuctionRepository auctionRepository;
    @Mock private AuctionBidRepository bidRepository;
    @Mock private EmailService emailService;
    @Mock private BidBroadcastService bidBroadcastService;
    @Mock private DomainEventPublisher events;
    @Mock private AuditService auditService;
    @Mock private PaymentService paymentService;

    private AuctionService auctionService;

    private User creator;
    private User bidder;
    private Auction openAuction;
    private Auction closedAuction;
    private Auction expiredAuction;

    @BeforeEach
    void setUp() {
        creator = makeUser(1L, "creator@test.com", "Creator");
        creator.setRole("COMPANY");
        bidder  = makeUser(2L, "bidder@test.com",  "Bidder");
        bidder.setRole("COMPANY");

        AuthorizationService authorizationService = new AuthorizationService();
        auctionService = new AuctionService(
                auctionRepository,
                bidRepository,
                new BusinessRuleValidator(authorizationService),
                authorizationService,
                bidBroadcastService,
                events,
                auditService,
                paymentService);

        openAuction = makeAuction(10L, "OPEN", 1000.0, 0.0,
                LocalDateTime.now().plusHours(2), creator);

        closedAuction = makeAuction(11L, "CLOSED", 500.0, 700.0,
                LocalDateTime.now().minusHours(1), creator);

        expiredAuction = makeAuction(12L, "OPEN", 1000.0, 0.0,
                LocalDateTime.now().minusMinutes(5), creator);   // deadline in past

        lenient().when(paymentService.hasPaidEmdForAuction(anyLong(), anyLong())).thenReturn(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static User makeUser(Long id, String email, String name) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setName(name);
        return u;
    }

    private static Auction makeAuction(Long id, String status,
            Double startingPrice, Double currentHighest,
            LocalDateTime deadline, User creator) {
        Auction a = new Auction();
        a.setId(id);
        a.setTitle("Test Auction " + id);
        a.setStatus(status);
        a.setStartingPrice(startingPrice);
        a.setCurrentHighestBid(currentHighest);
        a.setDeadline(deadline);
        a.setAuctionDate(LocalDate.now().minusDays(1));
        a.setAuctionTime(LocalTime.NOON);
        a.setCreatedBy(creator);
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // placeBid — happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("placeBid: valid bid above starting price returns SUCCESS")
    void placeBid_validAmount_returnsSuccess() {
        when(auctionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAuction));
        when(bidRepository.existsByAuctionIdAndBidderId(10L, 2L)).thenReturn(false);
        when(bidRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auctionRepository.saveAndFlush(any())).thenReturn(openAuction);

        String result = auctionService.placeBid(10L, bidder, 1500.0);

        assertEquals("SUCCESS", result);
        verify(bidRepository).saveAndFlush(any(AuctionBid.class));
        verify(auctionRepository).saveAndFlush(openAuction);
    }

    @Test
    @DisplayName("placeBid: updates currentHighestBid to submitted amount")
    void placeBid_updatesCurrentHighestBid() {
        when(auctionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAuction));
        when(bidRepository.existsByAuctionIdAndBidderId(anyLong(), anyLong())).thenReturn(false);
        when(bidRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auctionRepository.saveAndFlush(any())).thenReturn(openAuction);

        auctionService.placeBid(10L, bidder, 1800.0);

        assertEquals(1800.0, openAuction.getCurrentHighestBid());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // placeBid — guard conditions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("placeBid: auction not found returns NOT_FOUND")
    void placeBid_auctionNotFound_returnsNotFound() {
        when(auctionRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> auctionService.placeBid(99L, bidder, 5000.0));
        verify(bidRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeBid: CLOSED auction returns ALREADY_CLOSED")
    void placeBid_closedAuction_returnsAlreadyClosed() {
        when(auctionRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(closedAuction));
        assertEquals("ALREADY_CLOSED", auctionService.placeBid(11L, bidder, 5000.0));
    }

    @Test
    @DisplayName("placeBid: deadline passed returns ALREADY_CLOSED")
    void placeBid_pastDeadline_returnsAlreadyClosed() {
        when(auctionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(expiredAuction));
        assertEquals("ALREADY_CLOSED", auctionService.placeBid(12L, bidder, 5000.0));
    }

    @Test
    @DisplayName("placeBid: future start time returns NOT_STARTED")
    void placeBid_futureStartTime_returnsNotStarted() {
        Auction futureAuction = makeAuction(13L, "OPEN", 1000.0, 0.0,
                LocalDateTime.now().plusHours(2), creator);
        futureAuction.setAuctionDate(LocalDate.now().plusDays(1));
        futureAuction.setAuctionTime(LocalTime.NOON);
        when(auctionRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(futureAuction));

        assertEquals("NOT_STARTED", auctionService.placeBid(13L, bidder, 1500.0));
        verify(bidRepository, never()).saveAndFlush(any());
        verify(events, never()).publishAfterCommit(any());
    }

    @Test
    @DisplayName("lifecycleStatus: future, current, and expired auctions map to UPCOMING, LIVE, CLOSED")
    void lifecycleStatus_mapsByCurrentTime() {
        Auction upcoming = makeAuction(13L, "OPEN", 1000.0, 0.0,
                LocalDateTime.now().plusHours(2), creator);
        upcoming.setAuctionDate(LocalDate.now().plusDays(1));
        upcoming.setAuctionTime(LocalTime.NOON);

        Auction live = makeAuction(14L, "OPEN", 1000.0, 0.0,
                LocalDateTime.now().plusHours(1), creator);
        live.setAuctionDate(LocalDate.now().minusDays(1));
        live.setAuctionTime(LocalTime.NOON);

        Auction closed = makeAuction(15L, "OPEN", 1000.0, 0.0,
                LocalDateTime.now().minusMinutes(1), creator);

        assertEquals("UPCOMING", upcoming.getLifecycleStatus());
        assertEquals("LIVE", live.getLifecycleStatus());
        assertEquals("CLOSED", closed.getLifecycleStatus());
    }

    @Test
    @DisplayName("placeBid: creator bidding on own auction returns OWN_AUCTION")
    void placeBid_ownAuction_returnsOwnAuction() {
        when(auctionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAuction));
        // creator (id=1) tries to bid on their own auction
        assertEquals("OWN_AUCTION", auctionService.placeBid(10L, creator, 2000.0));
        verify(bidRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeBid: same bidder bids twice returns ALREADY_BID")
    void placeBid_duplicateBid_returnsAlreadyBid() {
        when(auctionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAuction));
        when(bidRepository.existsByAuctionIdAndBidderId(10L, 2L)).thenReturn(true);

        assertEquals("ALREADY_BID", auctionService.placeBid(10L, bidder, 2000.0));
        verify(bidRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeBid: amount at exactly starting price returns BID_TOO_LOW")
    void placeBid_amountEqualToStartingPrice_returnsBidTooLow() {
        when(auctionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAuction));
        when(bidRepository.existsByAuctionIdAndBidderId(anyLong(), anyLong())).thenReturn(false);
        // Starting price is 1000; bid at exactly 1000 is not strictly greater
        assertEquals("BID_TOO_LOW", auctionService.placeBid(10L, bidder, 1000.0));
    }

    @Test
    @DisplayName("placeBid: amount below current highest bid returns BID_TOO_LOW")
    void placeBid_amountBelowCurrentHighest_returnsBidTooLow() {
        Auction auctionWithBid = makeAuction(10L, "OPEN", 1000.0, 2000.0,
                LocalDateTime.now().plusHours(1), creator);
        when(auctionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(auctionWithBid));
        when(bidRepository.existsByAuctionIdAndBidderId(anyLong(), anyLong())).thenReturn(false);

        assertEquals("BID_TOO_LOW", auctionService.placeBid(10L, bidder, 1500.0));
    }

    @Test
    @DisplayName("placeBid: optimistic lock conflict returns CONCURRENT_BID")
    void placeBid_optimisticLockConflict_returnsConcurrentBid() {
        when(auctionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAuction));
        when(bidRepository.existsByAuctionIdAndBidderId(anyLong(), anyLong())).thenReturn(false);
        when(bidRepository.saveAndFlush(any())).thenReturn(new AuctionBid());
        when(auctionRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Auction.class, 10L));

        assertEquals("CONCURRENT_BID", auctionService.placeBid(10L, bidder, 1500.0));
    }

    @Test
    @DisplayName("placeBid: asynchronous notification scheduling does not block SUCCESS")
    void placeBid_notificationScheduling_returnsSuccess() {
        when(auctionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAuction));
        when(bidRepository.existsByAuctionIdAndBidderId(anyLong(), anyLong())).thenReturn(false);
        when(bidRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auctionRepository.saveAndFlush(any())).thenReturn(openAuction);

        assertEquals("SUCCESS", auctionService.placeBid(10L, bidder, 1500.0));
        verify(events, atLeastOnce()).publishAfterCommit(any(EmailNotificationRequested.class));
    }

    @Test
    @DisplayName("placeBid: bid against no-bid auction uses starting price as minimum")
    void placeBid_noPreviousBids_usesStartingPriceAsFloor() {
        // currentHighestBid = 0, startingPrice = 1000 → minimum is 1000
        when(auctionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAuction));
        when(bidRepository.existsByAuctionIdAndBidderId(anyLong(), anyLong())).thenReturn(false);

        assertEquals("BID_TOO_LOW", auctionService.placeBid(10L, bidder, 999.0));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // save — deadline computation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("save: deadline computed from date + time + duration")
    void save_computesDeadlineFromDateTimeDuration() {
        Auction a = new Auction();
        a.setTitle("Deadline Test");
        a.setAuctionDate(LocalDate.of(2030, 6, 1));
        a.setAuctionTime(LocalTime.of(10, 0));
        a.setDurationMinutes(90);
        when(auctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Auction saved = auctionService.save(a);

        assertEquals(LocalDateTime.of(2030, 6, 1, 11, 30), saved.getDeadline());
    }

    @Test
    @DisplayName("save: new auction with creator requests creation email")
    void save_newAuctionWithCreator_requestsCreationEmail() {
        Auction a = new Auction();
        a.setTitle("Email Test");
        a.setCreatedBy(creator);
        when(auctionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auctionService.save(a);

        verify(events).publishAfterCommit(argThat(event ->
                event instanceof EmailNotificationRequested notification
                        && "AUCTION_CREATED".equals(notification.notificationType())
                        && "creator@test.com".equals(notification.recipient())));
    }

    @Test
    @DisplayName("save: update (existing id) does NOT resend creation email")
    void save_updateExistingAuction_doesNotResendEmail() {
        openAuction.setId(10L);  // has existing id → isNew = false
        openAuction.setAuctionDate(LocalDate.now().plusDays(1));
        openAuction.setAuctionTime(LocalTime.NOON);
        when(auctionRepository.findById(10L)).thenReturn(Optional.of(openAuction));
        when(auctionRepository.save(any())).thenReturn(openAuction);

        auctionService.save(openAuction);

        verify(events, never()).publishAfterCommit(any(EmailNotificationRequested.class));
    }
}
