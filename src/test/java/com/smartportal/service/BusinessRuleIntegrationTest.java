package com.smartportal.service;

import com.smartportal.exception.ForbiddenException;
import com.smartportal.exception.ValidationException;
import com.smartportal.model.Auction;
import com.smartportal.model.Tender;
import com.smartportal.model.User;
import com.smartportal.repository.AuctionRepository;
import com.smartportal.repository.TenderRepository;
import com.smartportal.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class BusinessRuleIntegrationTest {

    @Autowired private TenderService tenderService;
    @Autowired private AuctionService auctionService;
    @Autowired private TenderRepository tenderRepository;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private UserRepository userRepository;

    @MockBean private EmailService emailService;

    @Test
    void tenderDurationLessThanThreeDaysFails() {
        User company = saveUser("Company", "company-duration@test.com", "9000000001", "COMPANY");
        Tender tender = tender("Short Tender", company, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> tenderService.save(tender, company));

        assertEquals("Tender duration must be at least 3 days.", ex.getMessage());
    }

    @Test
    void auctionDurationLessThanThirtyMinutesFails() {
        User company = saveUser("Company", "company-auction@test.com", "9000000002", "COMPANY");
        Auction auction = auction("Short Auction", company, LocalDate.now().plusDays(2), LocalTime.NOON, 20);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> auctionService.save(auction, company));

        assertEquals("Auction duration must be at least 30 minutes.", ex.getMessage());
    }

    @Test
    void companyCanCreateAndSearchOnlyOwnTenders() {
        User company = saveUser("Company", "company-own-tender@test.com", "9000000010", "COMPANY");
        User other = saveUser("Other Company", "company-other-tender@test.com", "9000000011", "COMPANY");

        Tender owned = tender("Company Road Tender", company, LocalDate.now().plusDays(5), LocalDate.now().plusDays(9));
        Tender otherTender = tender("Company Road Tender", other, LocalDate.now().plusDays(5), LocalDate.now().plusDays(9));
        tenderService.save(owned, company);
        tenderService.save(otherTender, other);

        Page<Tender> result = tenderService.searchByCreatedBy(company, "Road", "OPEN", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(company.getId(), result.getContent().get(0).getCreatedBy().getId());
    }

    @Test
    void governmentParticipationFails() {
        User creator = saveUser("Creator", "creator@test.com", "9000000003", "COMPANY");
        User government = saveUser("Government", "gov@test.com", "9000000004", "GOVERNMENT");

        Auction savedAuction = auctionRepository.save(
                auction("Open Auction", creator, LocalDate.now().plusDays(2), LocalTime.NOON, 60));
        savedAuction.setDeadline(LocalDateTime.now().plusDays(2));

        Tender savedTender = tenderRepository.save(
                tender("Open Tender", creator, LocalDate.now().plusDays(3), LocalDate.now().plusDays(7)));
        savedTender.setDeadline(LocalDateTime.now().plusDays(7));

        assertThrows(ForbiddenException.class,
                () -> auctionService.placeBid(savedAuction.getId(), government, 2000.0));
        assertThrows(ForbiddenException.class,
                () -> tenderService.fillTender(savedTender.getId(), government, 1000.0, "Proposal", null));
    }

    @Test
    void creatorCannotEditTenderInsideTwentyFourHourLock() {
        User creator = saveUser("Creator", "creator-lock@test.com", "9000000005", "COMPANY");
        Tender existing = tenderRepository.save(
                tender("Locked Tender", creator, LocalDate.now().plusDays(1), LocalDate.now().plusDays(5)));

        Tender update = tender("Locked Tender Updated", creator, existing.getStartDate(), existing.getEndDate());
        update.setId(existing.getId());

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> tenderService.save(update, creator));

        assertEquals("Cannot edit a tender within 24 hours before its start time.", ex.getMessage());
    }

    @Test
    void creatorCannotDeleteAuctionInsideOneHourLock() {
        User creator = saveUser("Creator", "creator-auction-delete-lock@test.com", "9000000012", "COMPANY");
        Auction existing = auctionRepository.save(
                auction("Locked Auction", creator, LocalDate.now(), LocalTime.now().plusMinutes(30), 60));
        existing.setDeadline(LocalDateTime.now().plusMinutes(90));

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> auctionService.deleteIfAllowed(existing.getId(), creator));

        assertEquals("Cannot edit an auction within 1 hour before its start time.", ex.getMessage());
    }

    @Test
    void adminCanDeleteAuctionInsideOneHourLock() {
        User creator = saveUser("Creator", "creator-admin-delete@test.com", "9000000013", "COMPANY");
        Auction existing = auctionRepository.save(
                auction("Admin Delete Auction", creator, LocalDate.now(), LocalTime.now().plusMinutes(30), 60));

        assertEquals("SUCCESS", auctionService.adminDelete(existing.getId()));
    }

    @Test
    void adminCanEditTenderInsideTwentyFourHourLock() {
        User creator = saveUser("Creator", "creator-admin@test.com", "9000000006", "COMPANY");
        User admin = saveUser("Admin", "admin@test.com", "9000000007", "ADMIN");
        Tender existing = tenderRepository.save(
                tender("Admin Editable Tender", creator, LocalDate.now().plusDays(1), LocalDate.now().plusDays(5)));

        Tender update = tender("Admin Updated Tender", admin, existing.getStartDate(), existing.getEndDate());
        update.setId(existing.getId());

        Tender saved = assertDoesNotThrow(() -> tenderService.save(update, admin));

        assertEquals("Admin Updated Tender", saved.getTitle());
    }

    private User saveUser(String name, String email, String mobile, String role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setMobile(mobile);
        user.setPassword("Password@123");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Tender tender(String title, User creator, LocalDate startDate, LocalDate endDate) {
        Tender tender = new Tender();
        tender.setTitle(title);
        tender.setDescription("Integration test tender");
        tender.setStartDate(startDate);
        tender.setEndDate(endDate);
        tender.setEstimatedBudget(1000.0);
        tender.setStatus("OPEN");
        tender.setCreatedBy(creator);
        return tender;
    }

    private Auction auction(String title, User creator, LocalDate date, LocalTime time, int durationMinutes) {
        Auction auction = new Auction();
        auction.setTitle(title);
        auction.setDescription("Integration test auction");
        auction.setAuctionDate(date);
        auction.setAuctionTime(time);
        auction.setDurationMinutes(durationMinutes);
        auction.setStartingPrice(1000.0);
        auction.setCurrentHighestBid(0.0);
        auction.setStatus("OPEN");
        auction.setCreatedBy(creator);
        return auction;
    }
}
