package com.smartportal.scheduler;

import com.smartportal.service.ClosureService;
import com.smartportal.service.SchedulerLockService;
import io.micrometer.core.instrument.Metrics;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuctionScheduler {
    private final ClosureService closureService;
    private final SchedulerLockService lockService;
    private final AtomicBoolean auctionRunning = new AtomicBoolean();
    private final AtomicBoolean tenderRunning = new AtomicBoolean();

    public AuctionScheduler(ClosureService closureService, SchedulerLockService lockService) {
        this.closureService = closureService;
        this.lockService = lockService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.auction.delay-ms:60000}")
    public void closeExpiredAuctions() {
        runGuarded("close-expired-auctions", Duration.ofMinutes(3), auctionRunning,
                closureService::closeExpiredAuctions, "auction");
    }

    @Scheduled(fixedDelayString = "${app.scheduler.tender.delay-ms:300000}")
    public void closeExpiredTenders() {
        runGuarded("close-expired-tenders", Duration.ofMinutes(10), tenderRunning,
                closureService::closeExpiredTenders, "tender");
    }

    private void runGuarded(String lockName, Duration lease, AtomicBoolean localGuard,
            CloseOperation operation, String type) {
        if (!localGuard.compareAndSet(false, true)) {
            Metrics.counter("smartportal.scheduler.skipped", "type", type, "reason", "local_overlap").increment();
            return;
        }
        boolean acquired = false;
        long started = System.nanoTime();
        try {
            acquired = lockService.tryAcquire(lockName, lease);
            if (!acquired) {
                Metrics.counter("smartportal.scheduler.skipped", "type", type, "reason", "distributed_lock").increment();
                return;
            }
            int closed = operation.close();
            Metrics.counter("smartportal.scheduler.closed", "type", type).increment(closed);
            Metrics.counter("smartportal.scheduler.runs", "type", type, "result", "success").increment();
        } catch (RuntimeException failure) {
            Metrics.counter("smartportal.scheduler.runs", "type", type, "result", "failure").increment();
            throw failure;
        } finally {
            if (acquired) lockService.release(lockName);
            localGuard.set(false);
            Metrics.timer("smartportal.scheduler.duration", "type", type)
                    .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    @FunctionalInterface
    private interface CloseOperation {
        int close();
    }
}
