package com.smartportal.controller;

import com.smartportal.model.*;
import com.smartportal.repository.UserRepository;
import com.smartportal.service.AuctionService;
import com.smartportal.service.TenderService;
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

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/government")
@PreAuthorize("hasRole('GOVERNMENT')")
public class GovernmentController {

    private final AuctionService auctionService;
    private final TenderService tenderService;
    private final UserRepository userRepository;
    private final com.smartportal.repository.TenderDocumentRepository tenderDocumentRepository;

    // H-3 FIX: externalized upload directory
    @Value("${app.upload.dir}")
    private String uploadDir;

    // L-4 FIX: allowed image MIME types
    private static final java.util.Set<String> ALLOWED_IMAGE_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    public GovernmentController(AuctionService auctionService,
            TenderService tenderService,
            UserRepository userRepository,
            com.smartportal.repository.TenderDocumentRepository tenderDocumentRepository) {
        this.auctionService = auctionService;
        this.tenderService = tenderService;
        this.userRepository = userRepository;
        this.tenderDocumentRepository = tenderDocumentRepository;
    }

    // ── Session helper ─────────────────────────────────
    private User getLoggedInUser(HttpSession session) {
        String email = (String) session.getAttribute("loggedInUser");
        if (email == null)
            return null;
        return userRepository.findByEmail(email).orElse(null);
    }

    private void addMeta(Model model, HttpSession session) {
        User user = getLoggedInUser(session);
        if (user != null) {
            model.addAttribute("govName", user.getName());
            model.addAttribute("govOrg", user.getOrganisation());
            model.addAttribute("govUser", user);
        }
    }

    // ── Guard: redirect if not GOVERNMENT ──────────────
    private boolean isNotGovt(HttpSession session) {
        String role = (String) session.getAttribute("userRole");
        return !"GOVERNMENT".equals(role);
    }

    // ══════════════════════════════════════════════════
    // DASHBOARD
    // ══════════════════════════════════════════════════
    @GetMapping({ "/", "/dashboard" })
    public String dashboard(Model model, HttpSession session) {
        if (isNotGovt(session))
            return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";

        model.addAttribute("myAuctions",
                auctionService.getByCreatedBy(user));
        model.addAttribute("myTenders",
                tenderService.getByCreatedBy(user));
        model.addAttribute("openAuctions",
                auctionService.getOpenByUser(user).size());
        model.addAttribute("closedAuctions",
                auctionService.getClosedByUser(user).size());
        model.addAttribute("openTenders",
                tenderService.getOpenByUser(user).size());
        model.addAttribute("closedTenders",
                tenderService.getClosedByUser(user).size());

        return "government/dashboard";
    }

    // ══════════════════════════════════════════════════
    // AUCTION MANAGEMENT
    // ══════════════════════════════════════════════════
    @GetMapping("/auctions")
    public String myAuctions(Model model, HttpSession session) {
        if (isNotGovt(session))
            return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        model.addAttribute("auctions", auctionService.getByCreatedBy(user));
        return "government/list_auction";
    }

    @GetMapping("/auction/create")
    public String createAuctionForm(Model model, HttpSession session) {
        if (isNotGovt(session))
            return "redirect:/login";
        addMeta(model, session);
        model.addAttribute("auction", new Auction());
        return "government/create_auction";
    }

    @PostMapping("/auction/save")
    public String saveAuction(@ModelAttribute Auction auction,
            @RequestParam(value = "imageFile", required = false) MultipartFile file,
            HttpSession session,
            RedirectAttributes ra) throws IOException {
        if (isNotGovt(session))
            return "redirect:/login";

        User user = getLoggedInUser(session);
        auction.setCreatedBy(user);

        // Image upload
        if (file != null && !file.isEmpty()) {
            // L-4 FIX: validate MIME type
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                ra.addFlashAttribute("error", "❌ Only JPEG, PNG, GIF, WebP images are allowed.");
                return "redirect:/government/auction/create";
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
            ra.addFlashAttribute("success", "Auction published successfully!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/government/auction/create";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/government/auctions";
    }

    @GetMapping("/auction/edit/{id}")
    public String editAuction(@PathVariable Long id,
            Model model, HttpSession session,
            RedirectAttributes ra) {
        if (isNotGovt(session))
            return "redirect:/login";
        addMeta(model, session);
        Auction auction = auctionService.getById(id);
        if (auction == null)
            return "redirect:/government/auctions";
        User user = getLoggedInUser(session);
        if (auction.getCreatedBy() == null ||
                !auction.getCreatedBy().getId().equals(user.getId())) {
            return "redirect:/government/auctions";
        }
        auctionService.validateEditable(id, user);
        model.addAttribute("auction", auction);
        return "government/update_auction";
    }

    @PostMapping("/auction/delete/{id}")
    public String deleteAuction(@PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotGovt(session))
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
        return "redirect:/government/auctions";
    }

    @GetMapping("/auction/view/{id}")
    public String viewAuction(@PathVariable Long id,
            Model model, HttpSession session) {
        if (isNotGovt(session))
            return "redirect:/login";
        addMeta(model, session);
        Auction auction = auctionService.getById(id);
        if (auction == null)
            return "redirect:/government/auctions";
        model.addAttribute("auction", auction);
        model.addAttribute("bids", auctionService.getBidsForAuction(id));
        return "government/view_auction";
    }

    // ══════════════════════════════════════════════════
    // TENDER MANAGEMENT
    // ══════════════════════════════════════════════════
    @GetMapping("/tenders")
    public String myTenders(Model model, HttpSession session) {
        if (isNotGovt(session))
            return "redirect:/login";
        addMeta(model, session);
        User user = getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        model.addAttribute("tenders", tenderService.getByCreatedBy(user));
        return "government/list_tender";
    }

    @GetMapping("/tender/create")
    public String createTenderForm(Model model, HttpSession session) {
        if (isNotGovt(session))
            return "redirect:/login";
        addMeta(model, session);
        model.addAttribute("tender", new Tender());
        return "government/create_tender";
    }

    @PostMapping("/tender/save")
    public String saveTender(@ModelAttribute Tender tender,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotGovt(session))
            return "redirect:/login";

        User user = getLoggedInUser(session);
        tender.setCreatedBy(user);

        try {
            tenderService.save(tender, user);
            ra.addFlashAttribute("success", "Tender published successfully!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/government/tender/create";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/government/tenders";
    }

    @GetMapping("/tender/edit/{id}")
    public String editTender(@PathVariable Long id,
            Model model, HttpSession session,
            RedirectAttributes ra) {
        if (isNotGovt(session))
            return "redirect:/login";
        addMeta(model, session);
        Tender tender = tenderService.getById(id);
        if (tender == null)
            return "redirect:/government/tenders";
        User user = getLoggedInUser(session);
        if (tender.getCreatedBy() == null ||
                !tender.getCreatedBy().getId().equals(user.getId())) {
            return "redirect:/government/tenders";
        }
        tenderService.validateEditable(id, user);
        model.addAttribute("tender", tender);
        return "government/update_tender";
    }

    @PostMapping("/tender/delete/{id}")
    public String deleteTender(@PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotGovt(session))
            return "redirect:/login";
        Tender tender = tenderService.getById(id);
        User user = getLoggedInUser(session);
        if (tender != null && tender.getCreatedBy() != null &&
                tender.getCreatedBy().getId().equals(user.getId())) {
            String result = tenderService.deleteIfAllowed(id, user);
            if ("SUCCESS".equals(result)) {
                ra.addFlashAttribute("success", "Tender deleted.");
            } else if ("ALREADY_STARTED".equals(result)) {
                ra.addFlashAttribute("error", "❌ Cannot delete a tender that has already started.");
            }
        } else {
            ra.addFlashAttribute("error", "You can only delete your own tenders.");
        }
        return "redirect:/government/tenders";
    }

    @GetMapping("/tender/view/{id}")
    public String viewTender(@PathVariable Long id,
            Model model, HttpSession session) {
        if (isNotGovt(session))
            return "redirect:/login";
        addMeta(model, session);
        Tender tender = tenderService.getById(id);
        if (tender == null)
            return "redirect:/government/tenders";
        model.addAttribute("tender", tender);
        model.addAttribute("bids", tenderService.getBidsForTender(id));
        model.addAttribute("documents", tenderDocumentRepository.findByTenderId(id));
        return "government/view_tender";
    }

    @PostMapping("/tender/document/upload/{id}")
    public String uploadTenderDocument(@PathVariable Long id,
            @RequestParam("documentName") String documentName,
            @RequestParam("file") MultipartFile file,
            HttpSession session,
            RedirectAttributes ra) throws IOException {
        if (isNotGovt(session))
            return "redirect:/login";
            
        Tender tender = tenderService.getById(id);
        User user = getLoggedInUser(session);
        
        if (tender == null || tender.getCreatedBy() == null || !tender.getCreatedBy().getId().equals(user.getId())) {
            ra.addFlashAttribute("error", "You can only upload documents to your own tenders.");
            return "redirect:/government/tenders";
        }
        
        if (file != null && !file.isEmpty()) {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);
            file.transferTo(uploadPath.resolve(fileName));
            
            TenderDocument doc = new TenderDocument();
            doc.setTender(tender);
            doc.setDocumentName(documentName);
            doc.setDocumentPath(fileName);
            tenderDocumentRepository.save(doc);
            ra.addFlashAttribute("success", "Document uploaded successfully!");
        } else {
            ra.addFlashAttribute("error", "File cannot be empty.");
        }
        
        return "redirect:/government/tender/view/" + id;
    }
}
