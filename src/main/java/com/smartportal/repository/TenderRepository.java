package com.smartportal.repository;

import com.smartportal.model.Tender;
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

public interface TenderRepository extends JpaRepository<Tender, Long> {

    @Override
    @EntityGraph(attributePaths = {"createdBy", "winner"})
Optional<Tender> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tender t where t.id = :id")
    Optional<Tender> findByIdForUpdate(@Param("id") Long id);

    // M-1 FIX: eager fetch createdBy + winner to avoid N+1 on list pages
    @EntityGraph(attributePaths = {"createdBy", "winner"})
    List<Tender> findByStatus(String status);

    @EntityGraph(attributePaths = {"createdBy", "winner"})
    Page<Tender> findByStatus(String status, Pageable pageable);

    // By creator using ID
    List<Tender> findByCreatedById(Long userId);

    // By creator using User object
    @EntityGraph(attributePaths = {"createdBy", "winner"})
    List<Tender> findByCreatedBy(User createdBy);

    @EntityGraph(attributePaths = {"createdBy", "winner"})
    @Query("""
            select t from Tender t
            where t.createdBy = :createdBy
              and (:status is null or :status = '' or t.status = :status)
              and (:search is null or :search = ''
                   or lower(t.title) like lower(concat('%', :search, '%'))
                   or lower(t.category) like lower(concat('%', :search, '%')))
            """)
    Page<Tender> searchByCreatedBy(@Param("createdBy") User createdBy,
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable);

    // By creator + status
    List<Tender> findByCreatedByAndStatus(User createdBy, String status);

    // For automatic closing / result declaration
    List<Tender> findByStatusAndDeadlineBeforeAndResultDeclaredFalse(
            String status,
            LocalDateTime now
    );
}
