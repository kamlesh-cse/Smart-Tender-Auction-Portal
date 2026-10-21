package com.smartportal.repository;

import com.smartportal.model.WinnerDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WinnerDeclarationRepository extends JpaRepository<WinnerDeclaration, Long> {
    boolean existsByEntityTypeAndEntityId(String entityType, Long entityId);
}
