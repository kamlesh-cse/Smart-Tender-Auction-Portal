package com.smartportal.repository;

import com.smartportal.model.TenderBid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface TenderBidRepository extends JpaRepository<TenderBid, Long> {
    @EntityGraph(attributePaths = {"company", "tender"})
    List<TenderBid> findByTenderIdOrderByBidAmountAsc(Long tenderId);
    Optional<TenderBid> findTopByTenderIdOrderByBidAmountAsc(Long tenderId);
    boolean existsByTenderIdAndCompanyId(Long tenderId, Long companyId);

    @EntityGraph(attributePaths = {"company", "tender", "tender.createdBy", "tender.winner"})
    List<TenderBid> findByCompanyId(Long companyId);
}
