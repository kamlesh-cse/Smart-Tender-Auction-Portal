package com.smartportal.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduler_locks")
public class SchedulerLock {
    @Id private String lockName;
    private LocalDateTime lockedUntil;
    private String lockedBy;
    @Version private Long version;

    public String getLockName() { return lockName; }
    public void setLockName(String value) { this.lockName = value; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime value) { this.lockedUntil = value; }
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String value) { this.lockedBy = value; }
}
