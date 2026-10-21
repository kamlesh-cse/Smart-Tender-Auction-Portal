package com.smartportal.service;

import com.smartportal.model.*;
import com.smartportal.repository.AuctionRepository;
import com.smartportal.repository.TenderRepository;
import com.smartportal.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private final TenderRepository tenderRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    
    public AdminService(TenderRepository tenderRepository,
                        AuctionRepository auctionRepository,
                        UserRepository userRepository) {
        this.tenderRepository = tenderRepository;
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
    }

    public String generateTendersCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Title,Category,Status,Budget,Created By,Start Date,End Date\n");
        List<Tender> tenders = tenderRepository.findAll();
        for (Tender t : tenders) {
            String createdBy = t.getCreatedBy() != null ? t.getCreatedBy().getEmail() : "N/A";
            csv.append(String.format("%d,\"%s\",\"%s\",\"%s\",%.2f,\"%s\",\"%s\",\"%s\"\n",
                t.getId(), t.getTitle(), t.getCategory(), t.getStatus(),
                t.getEstimatedBudget() != null ? t.getEstimatedBudget() : 0.0,
                createdBy, t.getStartDate(), t.getEndDate()
            ));
        }
        return csv.toString();
    }
    
    public String generateAuctionsCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Title,Status,Starting Price,Current Highest,Created By,Date,Time,Duration (min)\n");
        List<Auction> auctions = auctionRepository.findAll();
        for (Auction a : auctions) {
            String createdBy = a.getCreatedBy() != null ? a.getCreatedBy().getEmail() : "N/A";
            csv.append(String.format("%d,\"%s\",\"%s\",%.2f,%.2f,\"%s\",\"%s\",\"%s\",%d\n",
                a.getId(), a.getTitle(), a.getStatus(),
                a.getStartingPrice() != null ? a.getStartingPrice() : 0.0,
                a.getCurrentHighestBid() != null ? a.getCurrentHighestBid() : 0.0,
                createdBy, a.getAuctionDate(), a.getAuctionTime(), a.getDurationMinutes()
            ));
        }
        return csv.toString();
    }
    
    public String generateUsersCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Name,Email,Mobile,Role,Active,Created At\n");
        List<User> users = userRepository.findAll();
        for (User u : users) {
            csv.append(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",%b,\"%s\"\n",
                u.getId(), u.getName(), u.getEmail(), u.getMobile(),
                u.getRole(), u.isActive(), u.getCreatedAt()
            ));
        }
        return csv.toString();
    }
}
