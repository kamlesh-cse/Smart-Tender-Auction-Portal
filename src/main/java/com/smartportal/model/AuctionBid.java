package com.smartportal.model;

import jakarta.persistence.*;
import com.smartportal.persistence.MonetaryDoubleConverter;
import java.time.LocalDateTime;

@Entity
@Table(name = "auction_bids",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_auction_bid_participant",
				columnNames = {"auction_id", "bidder_id"}))
public class AuctionBid {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "auction_id", nullable = false)
	private Auction auction;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bidder_id", nullable = false)
	private User bidder;

	@Column(nullable = false)
	@Convert(converter = MonetaryDoubleConverter.class)
	private Double bidAmount;

	private LocalDateTime bidTime;

	@jakarta.persistence.PrePersist
	public void prePersist() {
		if (this.bidTime == null)
			this.bidTime = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Auction getAuction() {
		return auction;
	}

	public void setAuction(Auction auction) {
		this.auction = auction;
	}

	public User getBidder() {
		return bidder;
	}

	public void setBidder(User bidder) {
		this.bidder = bidder;
	}

	public Double getBidAmount() {
		return bidAmount;
	}

	public void setBidAmount(Double bidAmount) {
		this.bidAmount = bidAmount;
	}

	public LocalDateTime getBidTime() {
		return bidTime;
	}

	public void setBidTime(LocalDateTime bidTime) {
		this.bidTime = bidTime;
	}

}
