package com.smartportal.model;

import jakarta.persistence.*;
import com.smartportal.persistence.MonetaryDoubleConverter;
import java.time.LocalDateTime;

@Entity
@Table(name = "tender_bids",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_tender_bid_participant",
				columnNames = {"tender_id", "company_id"}))
public class TenderBid {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tender_id", nullable = false)
	private Tender tender;

	// Only COMPANY users can fill tenders
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_id", nullable = false)
	private User company;

	@Column(nullable = false)
	@Convert(converter = MonetaryDoubleConverter.class)
	private Double bidAmount;

	@Column(length = 2000)
	private String proposal;

	private String documentPath; // uploaded proposal document

	private LocalDateTime submittedAt;

	@jakarta.persistence.PrePersist
	public void prePersist() {
		if (this.submittedAt == null)
			this.submittedAt = LocalDateTime.now();
	}

	// PENDING, ACCEPTED, REJECTED
	private String status = "PENDING";

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Tender getTender() {
		return tender;
	}

	public void setTender(Tender tender) {
		this.tender = tender;
	}

	public User getCompany() {
		return company;
	}

	public void setCompany(User company) {
		this.company = company;
	}

	public Double getBidAmount() {
		return bidAmount;
	}

	public void setBidAmount(Double bidAmount) {
		this.bidAmount = bidAmount;
	}

	public String getProposal() {
		return proposal;
	}

	public void setProposal(String proposal) {
		this.proposal = proposal;
	}

	public String getDocumentPath() {
		return documentPath;
	}

	public void setDocumentPath(String documentPath) {
		this.documentPath = documentPath;
	}

	public LocalDateTime getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(LocalDateTime submittedAt) {
		this.submittedAt = submittedAt;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

}
