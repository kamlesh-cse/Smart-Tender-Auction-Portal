package com.smartportal.scheduler;

import com.smartportal.service.ClosureService;
import com.smartportal.service.SchedulerLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuctionSchedulerTest {
    private ClosureService closureService;
    private SchedulerLockService lockService;
    private AuctionScheduler scheduler;

    @BeforeEach
    void setUp() {
        closureService = mock(ClosureService.class);
        lockService = mock(SchedulerLockService.class);
        scheduler = new AuctionScheduler(closureService, lockService);
    }

    @Test
    void closesExpiredAuctionsWhenLockIsAcquired() {
        when(lockService.tryAcquire(eq("close-expired-auctions"), any(Duration.class))).thenReturn(true);
        when(closureService.closeExpiredAuctions()).thenReturn(2);

        scheduler.closeExpiredAuctions();

        verify(closureService).closeExpiredAuctions();
        verify(lockService).release("close-expired-auctions");
    }

    @Test
    void skipsAuctionClosureWhenAnotherInstanceOwnsLock() {
        when(lockService.tryAcquire(eq("close-expired-auctions"), any(Duration.class))).thenReturn(false);

        scheduler.closeExpiredAuctions();

        verifyNoInteractions(closureService);
        verify(lockService, never()).release(anyString());
    }

    @Test
    void closesExpiredTendersWhenLockIsAcquired() {
        when(lockService.tryAcquire(eq("close-expired-tenders"), any(Duration.class))).thenReturn(true);

        scheduler.closeExpiredTenders();

        verify(closureService).closeExpiredTenders();
        verify(lockService).release("close-expired-tenders");
    }
}
