package com.smartportal.repository;

import com.smartportal.model.Auction;
import com.smartportal.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    @Override
    @EntityGraph(attributePaths = {"createdBy", "winner"})
Optional<Auction> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Long id);

    // M-1 FIX: eager fetch createdBy + winner to avoid N+1 on list pages
    @EntityGraph(attributePaths = {"createdBy", "winner"})
    List<Auction> findByStatus(String status);

    @EntityGraph(attributePaths = {"createdBy", "winner"})
    Page<Auction> findByStatus(String status, Pageable pageable);

    // By creator using ID
    List<Auction> findByCreatedById(Long userId);

    // By creator using User object
    @EntityGraph(attributePaths = {"createdBy", "winner"})
    List<Auction> findByCreatedBy(User createdBy);

    // By creator + status
    List<Auction> findByCreatedByAndStatus(User createdBy, String status);

    // Auto-close logic support — only fetch auctions not yet processed
    List<Auction> findByStatusAndDeadlineBeforeAndResultDeclaredFalse(String status, LocalDateTime now);

    @EntityGraph(attributePaths = {"createdBy", "winner"})
    @Query("""
            select a from Auction a
            where a.status not in ('CLOSED', 'CANCELLED')
              and a.deadline < :now
              and a.resultDeclared = false
            """)
    List<Auction> findExpiredActiveAuctions(@Param("now") LocalDateTime now);
}
