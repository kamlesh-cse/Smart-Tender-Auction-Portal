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
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TenderService {

    private static final Logger log = LoggerFactory.getLogger(TenderService.class);

    private final TenderRepository tenderRepository;
    private final TenderBidRepository bidRepository;
    private final BusinessRuleValidator businessRuleValidator;
    private final AuthorizationService authorizationService;
    private final BidBroadcastService bidBroadcastService;
    private final DomainEventPublisher events;
    private final AuditService auditService;
    private final PaymentService paymentService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public TenderService(TenderRepository tenderRepository,
            TenderBidRepository bidRepository,
            BusinessRuleValidator businessRuleValidator,
            AuthorizationService authorizationService,
            BidBroadcastService bidBroadcastService,
            DomainEventPublisher events,
            AuditService auditService,
            PaymentService paymentService) {
        this.tenderRepository = tenderRepository;
        this.bidRepository = bidRepository;
        this.businessRuleValidator = businessRuleValidator;
        this.authorizationService = authorizationService;
        this.bidBroadcastService = bidBroadcastService;
        this.events = events;
        this.auditService = auditService;
        this.paymentService = paymentService;
    }

    // ── Save Tender ────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(cacheNames = "tenders", allEntries = true)
    public Tender save(Tender tender) {
        return saveInternal(tender, null, false, false);
    }

    /**
     * Save tender with optional admin bypass.
     * When isAdmin=true, the "already started" restriction is skipped.
     */
    @Transactional
    @CacheEvict(cacheNames = "tenders", allEntries = true)
    public Tender save(Tender tender, boolean isAdmin) {
        return saveInternal(tender, null, isAdmin, false);
    }

    @Transactional
    @CacheEvict(cacheNames = "tenders", allEntries = true)
    public Tender save(Tender tender, User actor) {
        return saveInternal(tender, actor, false, true);
    }

    private Tender saveInternal(Tender tender, User actor, boolean isAdmin, boolean enforceAuthorization) {
        boolean isNew = (tender.getId() == null);
        Tender existing = null;

        if (enforceAuthorization && isNew) {
            authorizationService.enforceCanCreateTender(actor);
        }

        if (!isNew) {
            existing = tenderRepository.findById(tender.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tender not found."));

            if (enforceAuthorization) {
                authorizationService.enforceCanEditOwnedResource(actor, existing.getCreatedBy(), "tender");
                businessRuleValidator.validateTenderEditAllowed(existing, actor);
            } else if (!isAdmin) {
                businessRuleValidator.validateTenderEditAllowed(existing, null);
            }

            existing.setTitle(tender.getTitle());
            existing.setDescription(tender.getDescription());
            existing.setCategory(tender.getCategory());
            existing.setStartDate(tender.getStartDate());
            existing.setEndDate(tender.getEndDate());
            existing.setEstimatedBudget(tender.getEstimatedBudget());
            if (isAdmin && tender.getCreatedBy() != null) {
                existing.setCreatedBy(tender.getCreatedBy());
            }
            tender = existing;
        }

        businessRuleValidator.validateTenderDuration(tender);

        if (tender.getEndDate() != null) {
            tender.setDeadline(businessRuleValidator.tenderDeadline(tender));
        }

        Tender saved = tenderRepository.save(tender);
        auditService.record(actor != null ? String.valueOf(actor.getId()) : (isAdmin ? "ADMIN" : "SYSTEM"),
                isNew ? "CREATE" : "EDIT", "TENDER:" + saved.getId(), "title=" + saved.getTitle());
        if (isNew && saved.getCreatedBy() != null && saved.getCreatedBy().getEmail() != null) {
            String deadline = saved.getEndDate() != null ? saved.getEndDate().format(DT_FMT) : "Not set";
            events.publishAfterCommit(email("tender:" + saved.getId() + ":created", "TENDER_CREATED",
                    saved.getCreatedBy().getEmail(), saved.getCreatedBy().getName(),
                    saved.getTitle(), deadline, null, "Tender"));
        }

        return saved;
    }

    // ── Basic Fetch Methods ────────────────────────────────────────────────────
    public List<Tender> getAll() {
        return tenderRepository.findAll();
    }

    public Page<Tender> getAll(Pageable pageable) {
        return tenderRepository.findAll(pageable);
    }

    public List<Tender> getOpen() {
        return tenderRepository.findByStatus("OPEN");
    }

    public Page<Tender> getOpen(Pageable pageable) {
        return tenderRepository.findByStatus("OPEN", pageable);
    }

    public List<Tender> getClosed() {
        return tenderRepository.findByStatus("CLOSED");
    }

    @Cacheable(cacheNames = "tenders", key = "#id")
    public Tender getById(Long id) {
        return tenderRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = "tenders", key = "#id")
    public String deleteIfNotStarted(Long id) {
        Tender tender = tenderRepository.findById(id).orElse(null);
        if (tender == null) return "NOT_FOUND";
        if (tender.isEditLocked()) return "EDIT_LOCKED";
        tenderRepository.deleteById(id);
        return "SUCCESS";
    }

    @CacheEvict(cacheNames = "tenders", key = "#id")
    public String deleteIfAllowed(Long id, User actor) {
        Tender tender = tenderRepository.findById(id).orElse(null);
        if (tender == null) return "NOT_FOUND";
        authorizationService.enforceCanEditOwnedResource(actor, tender.getCreatedBy(), "tender");
        businessRuleValidator.validateTenderDeleteAllowed(tender, actor);
        tenderRepository.deleteById(id);
        return "SUCCESS";
    }

    public void validateEditable(Long id, User actor) {
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender not found."));
        authorizationService.enforceCanEditOwnedResource(actor, tender.getCreatedBy(), "tender");
        businessRuleValidator.validateTenderEditAllowed(tender, actor);
    }

    /**
     * Admin-only: delete tender unconditionally (even if already started).
     */
    @Transactional
    @CacheEvict(cacheNames = "tenders", key = "#id")
    public String adminDelete(Long id) {
        Tender tender = tenderRepository.findById(id).orElse(null);
        if (tender == null) return "NOT_FOUND";
        tenderRepository.deleteById(id);
        return "SUCCESS";
    }

    public List<Tender> getByCreatedBy(User user) {
        return tenderRepository.findByCreatedBy(user);
    }

    public Page<Tender> searchByCreatedBy(User user, String search, String status, Pageable pageable) {
        return tenderRepository.searchByCreatedBy(user, search, status, pageable);
    }

    public List<Tender> getOpenByUser(User user) {
        return tenderRepository.findByCreatedByAndStatus(user, "OPEN");
    }

    public List<Tender> getClosedByUser(User user) {
        return tenderRepository.findByCreatedByAndStatus(user, "CLOSED");
    }

    // ── Fill Tender (Company Only) ─────────────────────────────────────────────
    @Transactional
    @CacheEvict(cacheNames = "tenders", key = "#tenderId")
    public String fillTender(Long tenderId, User company, Double amount,
            String proposal, String documentPath) {

        Tender tender = tenderRepository.findByIdForUpdate(tenderId).orElse(null);
        if (tender == null)
            throw new ResourceNotFoundException("Tender not found.");

        authorizationService.enforceCanParticipateInTender(company);

        // BUG-1 FIX: Creator cannot participate in their own tender
        if (tender.getCreatedBy() != null
                && tender.getCreatedBy().getId().equals(company.getId()))
            return "OWN_TENDER";

        if (!"OPEN".equals(tender.getStatus()))
            return "ALREADY_CLOSED";

        if (tender.getDeadline() != null
                && LocalDateTime.now().isAfter(tender.getDeadline()))
            return "ALREADY_CLOSED";

        if (bidRepository.existsByTenderIdAndCompanyId(tenderId, company.getId()))
            return "ALREADY_SUBMITTED";

        // EMD Gate: FR-5 requires EMD to be paid before submitting proposal
        if (!paymentService.hasPaidEmdForTender(tenderId, company.getId()))
            return "EMD_NOT_PAID";

        try {
            TenderBid bid = new TenderBid();
            bid.setTender(tender);
            bid.setCompany(company);
            bid.setBidAmount(amount);
            bid.setProposal(proposal);
            bid.setDocumentPath(documentPath);
            bid = bidRepository.saveAndFlush(bid);

            tenderRepository.saveAndFlush(tender);
            events.publishAfterCommit(new BidPlaced("TENDER", tenderId,
                    bidBroadcastService.createTenderPayload(bid), LocalDateTime.now()));
            auditService.record(String.valueOf(company.getId()), "BID", "TENDER:" + tenderId,
                    "bidId=" + bid.getId() + ",amount=" + amount);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent tender bid detected on tender {}: {}", tenderId, e.getMessage());
            return "CONCURRENT_BID";
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate tender submission detected on tender {} for company {}", tenderId, company.getId());
            return "ALREADY_SUBMITTED";
        }

        // Email company — confirmation
        try {
            events.publishAfterCommit(email("tender-bid:" + tenderId + ":" + company.getId() + ":company",
                    "TENDER_BID", company.getEmail(), company.getName(), tender.getTitle(),
                    null, amount, "Tender"));
        } catch (Exception e) {
            log.warn("Failed to send tender-submitted email to {}: {}", company.getEmail(), e.getMessage());
        }

        // Email tender creator — alert
        try {
            if (tender.getCreatedBy() != null && tender.getCreatedBy().getEmail() != null) {
                events.publishAfterCommit(email("tender-bid:" + tenderId + ":" + company.getId() + ":creator",
                        "TENDER_BID_ALERT", tender.getCreatedBy().getEmail(),
                        tender.getCreatedBy().getName(), tender.getTitle(), company.getName(),
                        amount, "Tender"));
            }
        } catch (Exception e) {
            log.warn("Failed to send tender-alert email to creator: {}", e.getMessage());
        }

        return "SUCCESS";
    }

    public List<TenderBid> getBidsForTender(Long tenderId) {
        return bidRepository.findByTenderIdOrderByBidAmountAsc(tenderId);
    }

    public List<TenderBid> getBidsByCompany(User user) {
        return bidRepository.findByCompanyId(user.getId());
    }

    private EmailNotificationRequested email(String key, String type, String to, String name,
            String title, String relatedName, Double amount, String entityType) {
        return new EmailNotificationRequested(key, type, to, name, title, relatedName,
                amount, entityType, LocalDateTime.now());
    }
}
