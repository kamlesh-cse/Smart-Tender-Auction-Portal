package com.smartportal.service;

import com.smartportal.model.SchedulerLock;
import com.smartportal.repository.SchedulerLockRepository;
import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulerLockService {
    private final SchedulerLockRepository repository;
    private final String owner;

    public SchedulerLockService(SchedulerLockRepository repository) {
        this.repository = repository;
        this.owner = resolveOwner();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String name, Duration duration) {
        LocalDateTime now = LocalDateTime.now();
        SchedulerLock lock = repository.findById(name).orElse(null);
        if (lock != null && lock.getLockedUntil() != null && lock.getLockedUntil().isAfter(now)) return false;
        if (lock == null) {
            lock = new SchedulerLock();
            lock.setLockName(name);
        }
        lock.setLockedBy(owner);
        lock.setLockedUntil(now.plus(duration));
        try {
            repository.saveAndFlush(lock);
            return true;
        } catch (DataIntegrityViolationException race) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String name) {
        repository.findById(name).ifPresent(lock -> {
            if (owner.equals(lock.getLockedBy())) {
                lock.setLockedUntil(LocalDateTime.now());
                repository.save(lock);
            }
        });
    }

    private String resolveOwner() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        } catch (Exception ignored) {
            return "instance-" + ProcessHandle.current().pid();
        }
    }
}
