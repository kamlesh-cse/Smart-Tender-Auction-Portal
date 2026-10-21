package com.smartportal.service;

import com.smartportal.event.DomainEventPublisher;
import com.smartportal.model.Auction;
import com.smartportal.repository.AuctionBidRepository;
import com.smartportal.repository.AuctionRepository;
import com.smartportal.repository.TenderBidRepository;
import com.smartportal.repository.TenderRepository;
import com.smartportal.repository.WinnerDeclarationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClosureServiceTest {
    @Mock AuctionRepository auctionRepository;
    @Mock TenderRepository tenderRepository;
    @Mock AuctionBidRepository auctionBidRepository;
    @Mock TenderBidRepository tenderBidRepository;
    @Mock WinnerDeclarationRepository declarationRepository;
    @Mock DomainEventPublisher events;
    @Mock AuditService auditService;

    @Test
    void duplicateCloseIsIgnored() {
        Auction auction = new Auction();
        auction.setId(10L);
        auction.setStatus("CLOSED");
        auction.setResultDeclared(true);
        auction.setDeclarationStatus("DECLARED");
        auction.setDeadline(LocalDateTime.now().minusMinutes(1));
        when(auctionRepository.findExpiredActiveAuctions(
                any())).thenReturn(List.of(auction));
        ClosureService service = new ClosureService(auctionRepository, tenderRepository,
                auctionBidRepository, tenderBidRepository, declarationRepository, events, auditService);

        assertEquals(0, service.closeExpiredAuctions());
        verify(auctionBidRepository, never()).findTopByAuctionIdOrderByBidAmountDesc(10L);
        verify(auctionRepository, never()).saveAndFlush(any());
    }

    @Test
    void closeWithoutBidsRunsOnce() {
        Auction auction = new Auction();
        auction.setId(11L);
        auction.setStatus("OPEN");
        auction.setDeadline(LocalDateTime.now().minusMinutes(1));
        when(auctionRepository.findExpiredActiveAuctions(
                any())).thenReturn(List.of(auction));
        when(auctionRepository.findById(11L)).thenReturn(Optional.of(auction));
        when(auctionBidRepository.findTopByAuctionIdOrderByBidAmountDesc(11L))
                .thenReturn(Optional.empty());
        ClosureService service = new ClosureService(auctionRepository, tenderRepository,
                auctionBidRepository, tenderBidRepository, declarationRepository, events, auditService);

        assertEquals(1, service.closeExpiredAuctions());
        assertEquals("DECLARED", auction.getDeclarationStatus());
        verify(auctionRepository).saveAndFlush(auction);
    }
}
