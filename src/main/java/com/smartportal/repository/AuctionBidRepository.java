package com.smartportal.repository;

import com.smartportal.model.AuctionBid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {
    @EntityGraph(attributePaths = {"bidder", "auction"})
    List<AuctionBid> findByAuctionIdOrderByBidAmountDesc(Long auctionId);
    Optional<AuctionBid> findTopByAuctionIdOrderByBidAmountDesc(Long auctionId);
    boolean existsByAuctionIdAndBidderId(Long auctionId, Long bidderId);

    @EntityGraph(attributePaths = {"bidder", "auction", "auction.createdBy", "auction.winner"})
    List<AuctionBid> findByBidderId(Long bidderId);
}
