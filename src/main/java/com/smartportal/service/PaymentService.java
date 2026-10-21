
package com.smartportal.service;

import com.smartportal.model.Auction;
import com.smartportal.model.EmdPayment;
import com.smartportal.model.Tender;
import com.smartportal.model.User;
import com.smartportal.repository.AuctionRepository;
import com.smartportal.repository.EmdPaymentRepository;
import com.smartportal.repository.TenderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final EmdPaymentRepository emdPaymentRepository;
    private final PaymentGateway paymentGateway;
    private final TenderRepository tenderRepository;
    private final AuctionRepository auctionRepository;

    public PaymentService(
            EmdPaymentRepository emdPaymentRepository,
            PaymentGateway paymentGateway,
            TenderRepository tenderRepository,
            AuctionRepository auctionRepository) {

        this.emdPaymentRepository = emdPaymentRepository;
        this.paymentGateway = paymentGateway;
        this.tenderRepository = tenderRepository;
        this.auctionRepository = auctionRepository;
    }

    @Transactional
    public String payEmdForTender(Long tenderId, User vendor, Double amount) {

        if (tenderId == null || vendor == null || vendor.getId() == null
                || amount == null || !Double.isFinite(amount) || amount <= 0) {
            return "FAILED";
        }

        Tender tender = tenderRepository.findById(tenderId).orElse(null);
        if (tender == null) {
            return "NOT_FOUND";
        }

        if (hasPaidEmdForTender(tenderId, vendor.getId())) {
            return "ALREADY_PAID";
        }

        try {
            String transactionId = paymentGateway.processPayment(amount);

            if (transactionId == null || transactionId.isBlank()) {
                return "FAILED";
            }

            EmdPayment payment = new EmdPayment();
            payment.setTender(tender);
            payment.setAuction(null);
            payment.setVendor(vendor);
            payment.setAmount(amount);
            payment.setTransactionId(transactionId);
            payment.setStatus("PAID");

            emdPaymentRepository.save(payment);
            return "SUCCESS";

        } catch (Exception e) {
            return "FAILED";
        }
    }

    @Transactional
    public String payEmdForAuction(Long auctionId, User vendor, Double amount) {

        if (auctionId == null || vendor == null || vendor.getId() == null
                || amount == null || !Double.isFinite(amount) || amount <= 0) {
            return "FAILED";
        }

        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            return "NOT_FOUND";
        }

        if (hasPaidEmdForAuction(auctionId, vendor.getId())) {
            return "ALREADY_PAID";
        }

        try {
            String transactionId = paymentGateway.processPayment(amount);

            if (transactionId == null || transactionId.isBlank()) {
                return "FAILED";
            }

            EmdPayment payment = new EmdPayment();
            payment.setTender(null);
            payment.setAuction(auction);
            payment.setVendor(vendor);
            payment.setAmount(amount);
            payment.setTransactionId(transactionId);
            payment.setStatus("PAID");

            emdPaymentRepository.save(payment);
            return "SUCCESS";

        } catch (Exception e) {
            return "FAILED";
        }
    }

    @Transactional(readOnly = true)
    public boolean hasPaidEmdForTender(Long tenderId, Long vendorId) {
        return emdPaymentRepository
                .findByTenderIdAndVendorIdAndStatus(tenderId, vendorId, "PAID")
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean hasPaidEmdForAuction(Long auctionId, Long vendorId) {
        return emdPaymentRepository
                .findByAuctionIdAndVendorIdAndStatus(auctionId, vendorId, "PAID")
                .isPresent();
    }
}