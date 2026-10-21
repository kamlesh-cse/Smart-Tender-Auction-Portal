package com.smartportal.service;

import com.smartportal.model.*;
import com.smartportal.exception.ForbiddenException;
import com.smartportal.exception.ResourceNotFoundException;
import com.smartportal.repository.TenderBidRepository;
import com.smartportal.repository.TenderRepository;
import com.smartportal.event.DomainEventPublisher;
import com.smartportal.event.EmailNotificationRequested;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenderServiceTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderBidRepository bidRepository;
    @Mock private EmailService emailService;
    @Mock private BidBroadcastService bidBroadcastService;
    @Mock private DomainEventPublisher events;
    @Mock private AuditService auditService;
    @Mock private PaymentService paymentService;

    private TenderService tenderService;

    private User govUser;
    private User company;
    private Tender openTender;
    private Tender closedTender;
    private Tender expiredTender;

    @BeforeEach
    void setUp() {
        govUser = makeUser(1L, "gov@test.com", "Government");
        govUser.setRole("GOVERNMENT");
        company = makeUser(2L, "co@test.com",  "Acme Ltd");
        company.setRole("COMPANY");

        AuthorizationService authorizationService = new AuthorizationService();
        tenderService = new TenderService(
                tenderRepository,
                bidRepository,
                new BusinessRuleValidator(authorizationService),
                authorizationService,
                bidBroadcastService,
                events,
                auditService,
                paymentService);

        openTender    = makeTender(10L, "OPEN",   LocalDateTime.now().plusDays(30), govUser);
        closedTender  = makeTender(11L, "CLOSED", LocalDateTime.now().minusDays(1), govUser);
        expiredTender = makeTender(12L, "OPEN",   LocalDateTime.now().minusMinutes(1), govUser);

        lenient().when(paymentService.hasPaidEmdForTender(anyLong(), anyLong())).thenReturn(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static User makeUser(Long id, String email, String name) {
        User u = new User();
        u.setId(id); u.setEmail(email); u.setName(name);
        return u;
    }

    private static Tender makeTender(Long id, String status,
            LocalDateTime deadline, User creator) {
        Tender t = new Tender();
        t.setId(id);
        t.setTitle("Tender " + id);
        t.setStatus(status);
        t.setDeadline(deadline);
        t.setEstimatedBudget(50000.0);
        t.setCreatedBy(creator);
        return t;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // fillTender — happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("fillTender: valid submission returns SUCCESS")
    void fillTender_validSubmission_returnsSuccess() {
        when(tenderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openTender));
        when(bidRepository.existsByTenderIdAndCompanyId(10L, 2L)).thenReturn(false);
        when(bidRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = tenderService.fillTender(10L, company, 45000.0, "Our proposal", null);

        assertEquals("SUCCESS", result);
        verify(bidRepository).saveAndFlush(any(TenderBid.class));
    }

    @Test
    @DisplayName("fillTender: saves bid with correct fields")
    void fillTender_savesBidWithCorrectFields() {
        when(tenderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openTender));
        when(bidRepository.existsByTenderIdAndCompanyId(anyLong(), anyLong())).thenReturn(false);
        when(bidRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        tenderService.fillTender(10L, company, 42000.0, "Full proposal text", "doc.pdf");

        ArgumentCaptor<TenderBid> captor = ArgumentCaptor.forClass(TenderBid.class);
        verify(bidRepository).saveAndFlush(captor.capture());
        TenderBid saved = captor.getValue();

        assertEquals(42000.0, saved.getBidAmount());
        assertEquals("Full proposal text", saved.getProposal());
        assertEquals("doc.pdf", saved.getDocumentPath());
        assertEquals(company, saved.getCompany());
        assertEquals(openTender, saved.getTender());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // fillTender — guard conditions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("fillTender: tender not found returns NOT_FOUND")
    void fillTender_tenderNotFound_returnsNotFound() {
        when(tenderRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> tenderService.fillTender(99L, company, 1000.0, "x", null));
        verify(bidRepository, never()).save(any());
    }

    @Test
    @DisplayName("fillTender: CLOSED tender returns ALREADY_CLOSED")
    void fillTender_closedTender_returnsAlreadyClosed() {
        when(tenderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(closedTender));
        assertEquals("ALREADY_CLOSED", tenderService.fillTender(11L, company, 1000.0, "x", null));
        verify(bidRepository, never()).save(any());
    }

    @Test
    @DisplayName("fillTender: past-deadline OPEN tender returns ALREADY_CLOSED")
    void fillTender_pastDeadline_returnsAlreadyClosed() {
        when(tenderRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(expiredTender));
        assertEquals("ALREADY_CLOSED", tenderService.fillTender(12L, company, 1000.0, "x", null));
    }

    @Test
    @DisplayName("fillTender: duplicate submission by same company returns ALREADY_SUBMITTED")
    void fillTender_duplicateSubmission_returnsAlreadySubmitted() {
        when(tenderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openTender));
        when(bidRepository.existsByTenderIdAndCompanyId(10L, 2L)).thenReturn(true);

        assertEquals("ALREADY_SUBMITTED", tenderService.fillTender(10L, company, 30000.0, "x", null));
        verify(bidRepository, never()).save(any());
    }

    @Test
    @DisplayName("fillTender: asynchronous notification scheduling does not block SUCCESS")
    void fillTender_notificationScheduling_returnsSuccess() {
        when(tenderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openTender));
        when(bidRepository.existsByTenderIdAndCompanyId(anyLong(), anyLong())).thenReturn(false);
        when(bidRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("SUCCESS", tenderService.fillTender(10L, company, 45000.0, "proposal", null));
        verify(events, atLeastOnce()).publishAfterCommit(any(EmailNotificationRequested.class));
    }

    @Test
    @DisplayName("fillTender: requests confirmation email to company")
    void fillTender_requestsConfirmationEmailToCompany() {
        when(tenderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openTender));
        when(bidRepository.existsByTenderIdAndCompanyId(anyLong(), anyLong())).thenReturn(false);
        when(bidRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        tenderService.fillTender(10L, company, 45000.0, "proposal", null);

        verify(events).publishAfterCommit(argThat(event ->
                event instanceof EmailNotificationRequested notification
                        && "TENDER_BID".equals(notification.notificationType())
                        && "co@test.com".equals(notification.recipient())));
    }

    @Test
    @DisplayName("fillTender: requests alert email to tender creator")
    void fillTender_requestsAlertToCreator() {
        when(tenderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openTender));
        when(bidRepository.existsByTenderIdAndCompanyId(anyLong(), anyLong())).thenReturn(false);
        when(bidRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        tenderService.fillTender(10L, company, 45000.0, "proposal", null);

        verify(events).publishAfterCommit(argThat(event ->
                event instanceof EmailNotificationRequested notification
                        && "TENDER_BID_ALERT".equals(notification.notificationType())
                        && "gov@test.com".equals(notification.recipient())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // save — deadline validation (L-3 FIX)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("save: end date in the past throws IllegalArgumentException")
    void save_pastEndDate_throwsException() {
        Tender t = new Tender();
        t.setTitle("Past Tender");
        t.setStartDate(LocalDate.now().minusDays(30));
        t.setEndDate(LocalDate.now().minusDays(1));   // ← in the past

        assertThrows(IllegalArgumentException.class, () -> tenderService.save(t));
        verify(tenderRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: duration less than 3 days throws IllegalArgumentException")
    void save_shortDuration_throwsException() {
        Tender t = new Tender();
        t.setTitle("Short Tender");
        t.setStartDate(LocalDate.now());
        t.setEndDate(LocalDate.now().plusDays(2));   // < 3 days

        assertThrows(IllegalArgumentException.class, () -> tenderService.save(t));
    }

    @Test
    @DisplayName("save: valid tender persists and computes midnight deadline")
    void save_validTender_setsDeadlineToEndOfDay() {
        Tender t = new Tender();
        t.setTitle("Valid Tender");
        t.setStartDate(LocalDate.now());
        t.setEndDate(LocalDate.now().plusDays(14));
        t.setCreatedBy(govUser);
        when(tenderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Tender saved = tenderService.save(t);

        assertNotNull(saved.getDeadline());
        // deadline should be at end of endDate (23:59:59.999...)
        assertEquals(LocalDate.now().plusDays(14), saved.getDeadline().toLocalDate());
        assertEquals(23, saved.getDeadline().getHour());
    }

    @Test
    @DisplayName("save: update inside edit lock throws IllegalStateException")
    void save_updateInsideEditLock_throwsException() {
        Tender existing = makeTender(5L, "OPEN", LocalDateTime.now().plusDays(1), govUser);
        existing.setStartDate(LocalDate.now().minusDays(30));
        existing.setEndDate(LocalDate.now().minusDays(1));  // past, but this is an update
        when(tenderRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(ForbiddenException.class, () -> tenderService.save(existing));
    }
}
