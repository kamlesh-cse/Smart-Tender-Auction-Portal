package com.smartportal.model;

import jakarta.persistence.*;
import com.smartportal.persistence.MonetaryDoubleConverter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tenders")
public class Tender {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(length = 2000)
	private String description;

	private String category; // Infrastructure, Technology, Supply Chain

	// Minimum 1 week duration enforced in service
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate startDate;

	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate endDate;

	// Deadline = endDate at 23:59:59
	private LocalDateTime deadline;

	@Convert(converter = MonetaryDoubleConverter.class)
	private Double estimatedBudget;

	// OPEN, CLOSED, CANCELLED
	private String status = "OPEN";

	// Winner = lowest bidder (declared within 1 hour after deadline)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "winner_id")
	private User winner;

	@Convert(converter = MonetaryDoubleConverter.class)
	private Double winningBid;
	@OneToMany(mappedBy = "tender", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TenderBid> bids = new ArrayList<>();

	private LocalDateTime createdAt;
	private boolean resultDeclared = false;
	private LocalDateTime resultDeclaredAt;
	private String declarationStatus = "PENDING";

	@Version
	private Long version;

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

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public LocalDateTime getDeadline() {
		return deadline;
	}

	public void setDeadline(LocalDateTime deadline) {
		this.deadline = deadline;
	}

	public Double getEstimatedBudget() {
		return estimatedBudget;
	}

	public void setEstimatedBudget(Double estimatedBudget) {
		this.estimatedBudget = estimatedBudget;
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

	public Double getWinningBid() {
		return winningBid;
	}

	public void setWinningBid(Double winningBid) {
		this.winningBid = winningBid;
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

	public LocalDateTime getResultDeclaredAt() {
		return resultDeclaredAt;
	}

	public void setResultDeclaredAt(LocalDateTime resultDeclaredAt) {
		this.resultDeclaredAt = resultDeclaredAt;
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

	public List<TenderBid> getBids() {
		return bids;
	}

	public void setBids(List<TenderBid> bids) {
		this.bids = bids;
	}

	/**
	 * Returns true if the tender has already started (current date >= startDate).
	 */
	public boolean isStarted() {
		if (startDate == null) return false;
		return !LocalDate.now().isBefore(startDate);
	}

	public boolean isEditLocked() {
		if (startDate == null) return false;
		return !LocalDateTime.now().isBefore(startDate.atStartOfDay().minusHours(24));
	}

}
