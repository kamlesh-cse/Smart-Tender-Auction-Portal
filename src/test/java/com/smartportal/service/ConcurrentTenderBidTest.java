package com.smartportal.service;

import com.smartportal.model.Tender;
import com.smartportal.model.User;
import com.smartportal.repository.TenderBidRepository;
import com.smartportal.repository.TenderRepository;
import com.smartportal.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.smartportal.service.PaymentService;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
class ConcurrentTenderBidTest {

    @Autowired TenderService tenderService;
    @Autowired TenderRepository tenderRepository;
    @Autowired TenderBidRepository bidRepository;
    @Autowired UserRepository userRepository;
    @MockBean PaymentService paymentService;

    @Test
    @DisplayName("Concurrent bids: only valid bids succeed, no data corruption")
    void concurrentBids_noDuplicatesOrCorruption() throws InterruptedException {
        // Setup: create tender creator
        User creator = new User();
        creator.setName("Gov Creator");
        creator.setEmail("gov-concurrent-" + System.currentTimeMillis() + "@test.com");
        creator.setMobile("8000000001");
        creator.setPassword("Password1234!");
        creator.setRole("GOVERNMENT");
        creator.setActive(true);
        creator = userRepository.save(creator);

        // Setup: create tender
        Tender tender = new Tender();
        tender.setTitle("Concurrent Test Tender");
        tender.setEstimatedBudget(50000.0);
        tender.setStatus("OPEN");
        tender.setCreatedBy(creator);
        tender.setStartDate(LocalDate.now().minusDays(1));
        tender.setEndDate(LocalDate.now().plusDays(24));
        tender.setDeadline(LocalDateTime.now().plusDays(24));
        tender = tenderRepository.save(tender);
        final Long tenderId = tender.getId();

        // Create 5 bidding companies
        List<User> companies = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User company = new User();
            company.setName("Company" + i);
            company.setEmail("co-concurrent-" + i + "-" + System.currentTimeMillis() + "@test.com");
            company.setMobile("800000" + String.format("%04d", i + 10));
            company.setPassword("Password1234!");
            company.setRole("COMPANY");
            company.setActive(true);
            companies.add(userRepository.save(company));
        }

        when(paymentService.hasPaidEmdForTender(anyLong(), anyLong())).thenReturn(true);

        // Execute: 5 threads bid concurrently with different amounts
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final User company = companies.get(i);
            final double amount = 45000.0 - (i * 1000); // 45k, 44k, 43k, 42k, 41k
            futures.add(executor.submit(() -> tenderService.fillTender(tenderId, company, amount, "Proposal " + amount, null)));
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

        // Verify no duplicate bids per company
        for (User company : companies) {
            long bidCount = bidRepository.findAll().stream()
                    .filter(b -> b.getTender().getId().equals(tenderId)
                            && b.getCompany().getId().equals(company.getId()))
                    .count();
            assertTrue(bidCount <= 1,
                    "Company " + company.getName() + " should have at most 1 bid, found " + bidCount);
        }
    }
}
