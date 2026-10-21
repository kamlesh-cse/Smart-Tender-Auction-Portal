package com.smartportal.controller;

import com.smartportal.model.*;
import com.smartportal.exception.ForbiddenException;
import com.smartportal.repository.UserRepository;
import com.smartportal.service.AuctionService;
import com.smartportal.service.TenderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/company")
@PreAuthorize("hasRole('COMPANY')")
public class CompanyController {

    private final AuctionService auctionService;
    private final TenderService tenderService;
    private final UserRepository userRepository;

    // H-3 FIX: externalized upload directory
    @Value("${app.upload.dir}")
    private String uploadDir;

    // L-4 FIX: allowed MIME types for images
    private static final java.util.Set<String> ALLOWED_IMAGE_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    // L-4 FIX: allowed MIME types for tender documents
    private static final java.util.Set<String> ALLOWED_DOC_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain");

    public CompanyController(AuctionService auctionService,
            TenderService tenderService,
            UserRepository userRepository) {
        this.auctionService = auctionService;
        this.tenderService = tenderService;
        this.userRepository = userRepository;
    }

    // ── Helpers ────────────────────────────────────────
    private User getLoggedInUser(HttpSession session) {
        String email = (String) session.getAttribute("loggedInUser");
        if (email == null)
            return null;
        return userRepository.findByEmail(email).orElse(null);
    }

    private void addMeta(Model model, HttpSession session) {
        User user = getLoggedInUser(session);
        if (user != null) {
            model.addAttribute("companyName", user.getName());
            model.addAttribute("companyOrg", user.getOrganisation());
            model.addAttribute("companyUser", user);
        }
    }

    private boolean isNotCompany(HttpSession session) {
        String role = (String) session.getAttribute("userRole");
        return !"COMPANY".equals(role);
    }

    @GetMapping({ "/", "/dashboard" })
    public String dashboard(Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";

        // Auctions created by this company
        List<Auction> myAuctions = auctionService.getByCreatedBy(user);
        // Bids placed by this company on auctions
        List<AuctionBid> myAuctionBids = auctionService.getBidsByUser(user);
        // Tender proposals submitted by this company
        List<TenderBid> myTenderBids = tenderService.getBidsByCompany(user);

        model.addAttribute("myAuctions", myAuctions);
        model.addAttribute("myAuctionBids", myAuctionBids);
        model.addAttribute("myTenderBids", myTenderBids);
        model.addAttribute("openAuctions", auctionService.getOpen().size());
        model.addAttribute("openTenders", tenderService.getOpen().size());
        model.addAttribute("wonAuctions",
                myAuctionBids.stream()
                        .filter(b -> b.getAuction().getWinner() != null
                                && b.getAuction().getWinner().getId().equals(user.getId()))
                        .count());
        model.addAttribute("wonTenders",
                myTenderBids.stream()
                        .filter(b -> b.getTender().getWinner() != null
                                && b.getTender().getWinner().getId().equals(user.getId()))
                        .count());

        return "company/dashboard";
    }

    // ══════════════════════════════════════════════════
    // BROWSE & BID ON AUCTIONS
    // ══════════════════════════════════════════════════
    @GetMapping("/auctions")
    public String browseAuctions(Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        model.addAttribute("auctions", auctionService.getAll());
        return "company/list_auction";
    }

    @GetMapping("/auction/view/{id}")
    public String viewAuction(@PathVariable Long id,
            Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        Auction auction = auctionService.getById(id);
        if (auction == null)
            return "redirect:/company/auctions";
        model.addAttribute("auction", auction);
        model.addAttribute("bids", auctionService.getBidsForAuction(id));

        // Check if this company already bid
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        boolean alreadyBid = auctionService.getBidsForAuction(id).stream()
                .anyMatch(b -> b.getBidder().getId().equals(user.getId()));
        model.addAttribute("alreadyBid", alreadyBid);
        model.addAttribute("isOwner",
                auction.getCreatedBy() != null
                        && auction.getCreatedBy().getId().equals(user.getId()));
        model.addAttribute("auctionStarted", auction.isStarted());
        model.addAttribute("auctionStartTime", auction.getStartDateTime());

        return "company/view_auction";
    }

    @PostMapping("/auction/bid/{id}")
    public String placeBid(@PathVariable Long id,
            @RequestParam Double bidAmount,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotCompany(session))
            return "redirect:/login";
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
                ra.addFlashAttribute("error",
                        "❌ This auction is closed.");
                break;
            case "NOT_STARTED":
                throw new ForbiddenException("Auction has not started yet.");
            case "ALREADY_BID":
                ra.addFlashAttribute("error",
                        "❌ You have already placed a bid on this auction.");
                break;
            case "CONCURRENT_BID":
                ra.addFlashAttribute("error",
                        "⚠️ Another bid was placed at the same time. Please refresh and try again.");
                break;
            default:
                ra.addFlashAttribute("error", "❌ Could not place bid.");
        }
        return "redirect:/company/auction/view/" + id;
    }

    // ── Company's own auctions (create/manage) ─────────
    @GetMapping("/my-auctions")
    public String myAuctions(Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        model.addAttribute("auctions", auctionService.getByCreatedBy(user));
        return "company/my_auctions";
    }

    @GetMapping("/auction/create")
    public String createAuctionForm(Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        model.addAttribute("auction", new Auction());
        return "company/create_auction";
    }

    @PostMapping("/auction/save")
    public String saveAuction(@ModelAttribute Auction auction,
            @RequestParam(value = "imageFile", required = false) MultipartFile file,
            HttpSession session,
            RedirectAttributes ra) throws IOException {
        if (isNotCompany(session))
            return "redirect:/login";
        User user = getLoggedInUser(session);
        auction.setCreatedBy(user);

        if (file != null && !file.isEmpty()) {
            // L-4 FIX: validate MIME type
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                ra.addFlashAttribute("error", "❌ Only JPEG, PNG, GIF, WebP images are allowed.");
                return "redirect:/company/auction/create";
            }
            String fileName = System.currentTimeMillis()
                    + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);
            file.transferTo(uploadPath.resolve(fileName));
            auction.setPhoto(fileName);
        } else if (auction.getId() != null) {
            Auction existing = auctionService.getById(auction.getId());
            if (existing != null)
                auction.setPhoto(existing.getPhoto());
        }

        try {
            auctionService.save(auction, user);
            ra.addFlashAttribute("success", "Auction published!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/company/auction/create";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/company/my-auctions";
    }

    @GetMapping("/auction/edit/{id}")
    public String editAuction(@PathVariable Long id,
            Model model, HttpSession session,
            RedirectAttributes ra) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        Auction auction = auctionService.getById(id);
        if (auction == null)
            return "redirect:/company/my-auctions";
        User user = getLoggedInUser(session);
        if (auction.getCreatedBy() == null ||
                !auction.getCreatedBy().getId().equals(user.getId())) {
            return "redirect:/company/my-auctions";
        }
        auctionService.validateEditable(id, user);
        model.addAttribute("auction", auction);
        return "company/update_auction";
    }

    @PostMapping("/auction/delete/{id}")
    public String deleteAuction(@PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotCompany(session))
            return "redirect:/login";
        Auction auction = auctionService.getById(id);
        User user = getLoggedInUser(session);
        if (auction != null && auction.getCreatedBy() != null &&
                auction.getCreatedBy().getId().equals(user.getId())) {
            String result = auctionService.deleteIfAllowed(id, user);
            if ("SUCCESS".equals(result)) {
                ra.addFlashAttribute("success", "Auction deleted.");
            } else if ("ALREADY_STARTED".equals(result)) {
                ra.addFlashAttribute("error", "❌ Cannot delete an auction that has already started.");
            }
        } else {
            ra.addFlashAttribute("error", "You can only delete your own auctions.");
        }
        return "redirect:/company/my-auctions";
    }

    // ── My Auction Bids ────────────────────────────────
    @GetMapping("/my-bids")
    public String myBids(Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        model.addAttribute("auctionBids", auctionService.getBidsByUser(user));
        model.addAttribute("tenderBids", tenderService.getBidsByCompany(user));
        return "company/my_bids";
    }

    // ══════════════════════════════════════════════════
    // TENDERS — Browse & Submit Proposals
    // ══════════════════════════════════════════════════
    @GetMapping("/tenders")
    public String browseTenders(Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        model.addAttribute("tenders", tenderService.getAll());
        return "company/list_tender";
    }

    @GetMapping("/my-tenders")
    public String myTenders(@RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";

        Pageable pageable = PageRequest.of(Math.max(page, 0), 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Tender> tenderPage = tenderService.searchByCreatedBy(user, search, status, pageable);
        model.addAttribute("tenderPage", tenderPage);
        model.addAttribute("tenders", tenderPage.getContent());
        model.addAttribute("search", search);
        model.addAttribute("status", status);
        return "company/my_tenders";
    }

    @GetMapping("/tender/create")
    public String createTenderForm(Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        model.addAttribute("tender", new Tender());
        return "company/create_tender";
    }

    @PostMapping({"/tender/create", "/tender/save"})
    public String saveTender(@ModelAttribute Tender tender,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotCompany(session))
            return "redirect:/login";

        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        tender.setCreatedBy(user);

        try {
            tenderService.save(tender, user);
            ra.addFlashAttribute("success", "Tender published successfully!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/company/tender/create";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
            return "redirect:/company/tender/create";
        }
        return "redirect:/company/my-tenders";
    }

    @GetMapping("/tender/edit/{id}")
    public String editTender(@PathVariable Long id,
            Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        tenderService.validateEditable(id, user);
        Tender tender = tenderService.getById(id);
        model.addAttribute("tender", tender);
        return "company/create_tender";
    }

    @PostMapping("/tender/delete/{id}")
    public String deleteTender(@PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotCompany(session))
            return "redirect:/login";
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        String result = tenderService.deleteIfAllowed(id, user);
        if ("SUCCESS".equals(result)) {
            ra.addFlashAttribute("success", "Tender deleted.");
        }
        return "redirect:/company/my-tenders";
    }

    @GetMapping("/tender/view/{id}")
    public String viewTender(@PathVariable Long id,
            Model model, HttpSession session) {
        if (isNotCompany(session))
            return "redirect:/login";
        addMeta(model, session);
        Tender tender = tenderService.getById(id);
        if (tender == null)
            return "redirect:/company/tenders";
        model.addAttribute("tender", tender);
        model.addAttribute("bids", tenderService.getBidsForTender(id));

        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        boolean alreadySubmitted = tenderService.getBidsForTender(id).stream()
                .anyMatch(b -> b.getCompany().getId().equals(user.getId()));
        model.addAttribute("alreadySubmitted", alreadySubmitted);

        return "company/view_tender";
    }

    @PostMapping("/tender/submit/{id}")
    public String submitTenderProposal(
            @PathVariable Long id,
            @RequestParam Double bidAmount,
            @RequestParam(required = false) String proposal,
            @RequestParam(value = "docFile", required = false) MultipartFile docFile,
            HttpSession session,
            RedirectAttributes ra) throws IOException {

        if (isNotCompany(session))
            return "redirect:/login";
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";

        String docPath = null;
        if (docFile != null && !docFile.isEmpty()) {
            // L-4 FIX: validate MIME type for documents
            String contentType = docFile.getContentType();
            if (contentType == null || !ALLOWED_DOC_TYPES.contains(contentType)) {
                ra.addFlashAttribute("error", "❌ Only PDF, Word, Excel, images, and text files are allowed.");
                return "redirect:/company/tender/view/" + id;
            }
            String fileName = System.currentTimeMillis()
                    + "_" + docFile.getOriginalFilename();
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);
            docFile.transferTo(uploadPath.resolve(fileName));
            docPath = fileName;
        }

        String result = tenderService.fillTender(id, user, bidAmount, proposal, docPath);
        switch (result) {
            case "SUCCESS":
                ra.addFlashAttribute("success", "✅ Proposal submitted successfully!");
                break;
            case "OWN_TENDER":
                // BUG-1 FIX: Creator cannot participate in their own tender
                ra.addFlashAttribute("error", "❌ You cannot submit a proposal on your own tender.");
                break;
            case "ALREADY_SUBMITTED":
                ra.addFlashAttribute("error", "❌ You already submitted a proposal for this tender.");
                break;
            case "ALREADY_CLOSED":
                ra.addFlashAttribute("error", "❌ This tender is no longer accepting proposals.");
                break;
            default:
                ra.addFlashAttribute("error", "❌ Could not submit proposal.");
        }
        return "redirect:/company/tender/view/" + id;
    }
}
