package com.smartportal.repository;

import com.smartportal.model.SchedulerLock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerLockRepository extends JpaRepository<SchedulerLock, String> {
}
