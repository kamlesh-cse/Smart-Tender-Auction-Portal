package com.smartportal.repository;

import com.smartportal.model.EmdPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmdPaymentRepository extends JpaRepository<EmdPayment, Long> {

    Optional<EmdPayment> findByTenderIdAndVendorIdAndStatus(
            Long tenderId,
            Long vendorId,
            String status);

    Optional<EmdPayment> findByAuctionIdAndVendorIdAndStatus(
            Long auctionId,
            Long vendorId,
            String status);
}