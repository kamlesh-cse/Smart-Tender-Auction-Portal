package com.smartportal.model;

import jakarta.persistence.*;
import com.smartportal.persistence.MonetaryDoubleConverter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "auctions")
public class Auction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(length = 2000)
	private String description;

	private String photo;

	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate auctionDate;

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime auctionTime;

	// Duration in minutes (minimum 30)
	private Integer durationMinutes;

	// Deadline = auctionDate + auctionTime + durationMinutes
	private LocalDateTime deadline;

	@Convert(converter = MonetaryDoubleConverter.class)
	private Double startingPrice;
	@Convert(converter = MonetaryDoubleConverter.class)
	private Double currentHighestBid = 0.0;

	// OPEN, CLOSED, CANCELLED
	private String status = "OPEN";

	// Creator (Government or Company or Admin)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	// Winner (declared after deadline)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "winner_id")
	private User winner;

	@OneToMany(mappedBy = "auction", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AuctionBid> bids = new ArrayList<>();

	@Version
	private Long version;

	private LocalDateTime createdAt;
	private boolean resultDeclared = false;
	private String declarationStatus = "PENDING";

	@jakarta.persistence.PrePersist
	public void prePersist() {
		if (this.createdAt == null)
			this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getPhoto() {
		return photo;
	}

	public void setPhoto(String photo) {
		this.photo = photo;
	}

	public LocalDate getAuctionDate() {
		return auctionDate;
	}

	public void setAuctionDate(LocalDate auctionDate) {
		this.auctionDate = auctionDate;
	}

	public LocalTime getAuctionTime() {
		return auctionTime;
	}

	public void setAuctionTime(LocalTime auctionTime) {
		this.auctionTime = auctionTime;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public LocalDateTime getDeadline() {
		return deadline;
	}

	public void setDeadline(LocalDateTime deadline) {
		this.deadline = deadline;
	}

	public Double getStartingPrice() {
		return startingPrice;
	}

	public void setStartingPrice(Double startingPrice) {
		this.startingPrice = startingPrice;
	}

	public Double getCurrentHighestBid() {
		return currentHighestBid;
	}

	public void setCurrentHighestBid(Double currentHighestBid) {
		this.currentHighestBid = currentHighestBid;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(User createdBy) {
		this.createdBy = createdBy;
	}

	public User getWinner() {
		return winner;
	}

	public void setWinner(User winner) {
		this.winner = winner;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public boolean isResultDeclared() {
		return resultDeclared;
	}

	public void setResultDeclared(boolean resultDeclared) {
		this.resultDeclared = resultDeclared;
	}

	public String getDeclarationStatus() {
		return declarationStatus;
	}

	public void setDeclarationStatus(String declarationStatus) {
		this.declarationStatus = declarationStatus;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public List<AuctionBid> getBids() {
		return bids;
	}

	public void setBids(List<AuctionBid> bids) {
		this.bids = bids;
	}

	/**
	 * Returns the start date-time of this auction (auctionDate + auctionTime).
	 */
	public LocalDateTime getStartDateTime() {
		if (auctionDate != null && auctionTime != null) {
			return LocalDateTime.of(auctionDate, auctionTime);
		}
		return null;
	}

	/**
	 * Returns true if the auction has already started (current time >= start time).
	 */
	public boolean isStarted() {
		LocalDateTime start = getStartDateTime();
		if (start == null) return false;
		return !LocalDateTime.now().isBefore(start);
	}

	public String getLifecycleStatus() {
		return AuctionLifecycle.status(status, getStartDateTime(), deadline);
	}

	public boolean isUpcoming() {
		return AuctionLifecycle.isUpcoming(status, getStartDateTime(), deadline);
	}

	public boolean isLive() {
		return AuctionLifecycle.isLive(status, getStartDateTime(), deadline);
	}

	public boolean isClosedForBidding() {
		return AuctionLifecycle.isClosedForBidding(status, getStartDateTime(), deadline);
	}

	public boolean isEditLocked() {
		LocalDateTime start = getStartDateTime();
		if (start == null) return false;
		return !LocalDateTime.now().isBefore(start.minusHours(1));
	}

}
