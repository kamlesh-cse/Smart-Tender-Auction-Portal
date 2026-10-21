package com.smartportal.controller;

import com.smartportal.model.*;
import com.smartportal.exception.ForbiddenException;
import com.smartportal.repository.UserRepository;
import com.smartportal.service.AuctionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Controller
@RequestMapping("/user")
@PreAuthorize("hasRole('USER')")
public class UserController {

    private final AuctionService auctionService;
    private final UserRepository userRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private static final java.util.Set<String> ALLOWED_IMAGE_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    public UserController(AuctionService auctionService,
                          UserRepository userRepository) {
        this.auctionService = auctionService;
        this.userRepository = userRepository;
    }

    // ── Helpers ────────────────────────────────────────
    private User getLoggedInUser(HttpSession session) {
        String email = (String) session.getAttribute("loggedInUser");
        if (email == null) return null;
        return userRepository.findByEmail(email).orElse(null);
    }

    private void addMeta(Model model, HttpSession session) {
        User user = getLoggedInUser(session);
        if (user != null) {
            model.addAttribute("userName", user.getName());
            model.addAttribute("userObj",  user);
        }
    }

    private boolean isNotUser(HttpSession session) {
        String role = (String) session.getAttribute("userRole");
        return !"USER".equals(role);
    }

    // ══════════════════════════════════════════════════
    //  DASHBOARD
    // ══════════════════════════════════════════════════
    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        if (isNotUser(session)) return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";

        List<AuctionBid> myBids = auctionService.getBidsByUser(user);
        List<Auction> myAuctions = auctionService.getByCreatedBy(user);
        long wonCount = myBids.stream()
            .filter(b -> b.getAuction().getWinner() != null
                && b.getAuction().getWinner().getId().equals(user.getId()))
            .count();
        long lostCount = myBids.stream()
            .filter(b -> b.getAuction().getStatus().equals("CLOSED")
                && (b.getAuction().getWinner() == null
                    || !b.getAuction().getWinner().getId().equals(user.getId())))
            .count();

        model.addAttribute("myBids",      myBids);
        model.addAttribute("myAuctions",  myAuctions);
        model.addAttribute("wonCount",    wonCount);
        model.addAttribute("lostCount",   lostCount);
        model.addAttribute("openAuctions", auctionService.getOpen().size());
        model.addAttribute("recentAuctions",
            auctionService.getOpen().stream().limit(5).toList());

        return "user/dashboard";
    }

    // ══════════════════════════════════════════════════
    //  BROWSE AUCTIONS
    // ══════════════════════════════════════════════════
    @GetMapping("/auctions")
    public String browseAuctions(Model model, HttpSession session) {
        if (isNotUser(session)) return "redirect:/login";
        addMeta(model, session);
        model.addAttribute("auctions", auctionService.getAll());
        return "user/list_auction";
    }

    @GetMapping("/auction/view/{id}")
    public String viewAuction(@PathVariable Long id,
                              Model model, HttpSession session) {
        if (isNotUser(session)) return "redirect:/login";
        addMeta(model, session);

        Auction auction = auctionService.getById(id);
        if (auction == null) return "redirect:/user/auctions";

        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        List<AuctionBid> bids = auctionService.getBidsForAuction(id);

        boolean alreadyBid = bids.stream()
            .anyMatch(b -> b.getBidder().getId().equals(user.getId()));
        boolean isOwner = auction.getCreatedBy() != null
            && auction.getCreatedBy().getId().equals(user.getId());

        model.addAttribute("auction",    auction);
        model.addAttribute("bids",       bids);
        model.addAttribute("alreadyBid", alreadyBid);
        model.addAttribute("isOwner",    isOwner);
        model.addAttribute("auctionStarted", auction.isStarted());
        model.addAttribute("auctionStartTime", auction.getStartDateTime());

        return "user/view_auction";
    }

    @PostMapping("/auction/bid/{id}")
    public String placeBid(@PathVariable Long id,
                           @RequestParam Double bidAmount,
                           HttpSession session,
                           RedirectAttributes ra) {
        if (isNotUser(session)) return "redirect:/login";
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";

        String result = auctionService.placeBid(id, user, bidAmount);
        switch (result) {
            case "SUCCESS":
                ra.addFlashAttribute("success", "✅ Bid placed successfully!");
                break;
            case "BID_TOO_LOW":
                ra.addFlashAttribute("error",
                    "❌ Bid must be higher than the current highest bid.");
                break;
            case "OWN_AUCTION":
                throw new ForbiddenException("You cannot bid on your own auction.");
            case "ALREADY_CLOSED":
                ra.addFlashAttribute("error", "❌ This auction has closed.");
                break;
            case "NOT_STARTED":
                throw new ForbiddenException("Auction has not started yet.");
            default:
                ra.addFlashAttribute("error", "❌ Could not place bid.");
        }
        return "redirect:/user/auction/view/" + id;
    }

    // ══════════════════════════════════════════════════
    //  MY BIDS
    // ══════════════════════════════════════════════════
    @GetMapping("/my-bids")
    public String myBids(Model model, HttpSession session) {
        if (isNotUser(session)) return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        model.addAttribute("bids", auctionService.getBidsByUser(user));
        return "user/my_bids";
    }


}
