package com.smartportal.controller;

import com.smartportal.model.*;
import com.smartportal.repository.UserRepository;
import com.smartportal.service.AuctionService;
import com.smartportal.service.TenderService;
import com.smartportal.service.AdminService;
import com.smartportal.model.UserRole;
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
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuctionService auctionService;
    private final TenderService tenderService;
    private final UserRepository userRepository;
    private final AdminService adminService;
    private final com.smartportal.repository.TenderDocumentRepository tenderDocumentRepository;

    // H-3 FIX: externalized upload directory
    @Value("${app.upload.dir}")
    private String uploadDir;

    // L-4 FIX: allowed image MIME types
    private static final java.util.Set<String> ALLOWED_IMAGE_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    public AdminController(AuctionService auctionService,
            TenderService tenderService,
            UserRepository userRepository,
            AdminService adminService,
            com.smartportal.repository.TenderDocumentRepository tenderDocumentRepository) {
        this.auctionService = auctionService;
        this.tenderService = tenderService;
        this.userRepository = userRepository;
        this.adminService = adminService;
        this.tenderDocumentRepository = tenderDocumentRepository;
    }

    // ── helper: deny non-admin access ──────────────────
    private boolean isNotAdmin(HttpSession session) {
        String role = (String) session.getAttribute("userRole");
        return !"ADMIN".equals(role);
    }

    // ── helper: add common model attributes ────────────
    private void addAdminMeta(Model model, HttpSession session) {
        String email = (String) session.getAttribute("loggedInUser");
        String displayName = "Admin";
        if (email != null && !email.equalsIgnoreCase("admin")) {
            displayName = userRepository.findByEmail(email)
                    .map(u -> u.getName())
                    .orElse(email);
        }
        model.addAttribute("adminName", displayName);
    }

    // ══════════════════════════════════════════════════
    // DASHBOARD
    // ══════════════════════════════════════════════════
    @GetMapping({ "/", "/dashboard" })
    public String dashboard(Model model, HttpSession session) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);

        // Stats
        List<User> allUsers = userRepository.findAll();
        model.addAttribute("totalUsers", allUsers.size());
        model.addAttribute("govtUsers", userRepository.findByRole(UserRole.GOVERNMENT).size());
        model.addAttribute("companyUsers", userRepository.findByRole(UserRole.COMPANY).size());
        model.addAttribute("regularUsers", userRepository.findByRole(UserRole.USER).size());

        model.addAttribute("totalAuctions", auctionService.getAll().size());
        model.addAttribute("openAuctions", auctionService.getOpen().size());
        model.addAttribute("closedAuctions", auctionService.getClosed().size());

        model.addAttribute("totalTenders", tenderService.getAll().size());
        model.addAttribute("openTenders", tenderService.getOpen().size());
        model.addAttribute("closedTenders", tenderService.getClosed().size());

        // Recent records
        model.addAttribute("recentAuctions", auctionService.getAll()
                .stream().limit(5).toList());
        model.addAttribute("recentTenders", tenderService.getAll()
                .stream().limit(5).toList());
        model.addAttribute("recentUsers", allUsers
                .stream().limit(5).toList());

        return "admin/dashboard";
    }

    // ══════════════════════════════════════════════════
    // USER MANAGEMENT
    // ══════════════════════════════════════════════════
    @GetMapping("/users")
    public String listUsers(Model model, HttpSession session) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);
        model.addAttribute("users", userRepository.findAll());
        return "admin/users";
    }

    @PostMapping("/user/toggle/{id}")
    public String toggleUser(@PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotAdmin(session))
            return "redirect:/login";
        userRepository.findById(id).ifPresent(u -> {
            u.setActive(!u.isActive());
            userRepository.save(u);
        });
        ra.addFlashAttribute("success", "User status updated.");
        return "redirect:/admin/users";
    }

    // ══════════════════════════════════════════════════
    // AUCTION MANAGEMENT
    // ══════════════════════════════════════════════════
    @GetMapping("/auctions")
    public String listAuctions(Model model, HttpSession session) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);
        model.addAttribute("auctions", auctionService.getAll());
        return "admin/list_auction";
    }

    @GetMapping("/auction/create")
    public String createAuctionForm(Model model, HttpSession session) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);
        model.addAttribute("auction", new Auction());
        return "admin/create_auction";
    }

    @PostMapping("/auction/save")
    public String saveAuction(@ModelAttribute Auction auction,
            @RequestParam(value = "imageFile", required = false) MultipartFile file,
            HttpSession session,
            RedirectAttributes ra) throws IOException {
        if (isNotAdmin(session))
            return "redirect:/login";

        // Set creator from session
        String email = (String) session.getAttribute("loggedInUser");
        if (email != null && !email.equals("admin")) {
            userRepository.findByEmail(email)
                    .ifPresent(auction::setCreatedBy);
        }

        // Handle image
        if (file != null && !file.isEmpty()) {
            // L-4 FIX: validate MIME type
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                ra.addFlashAttribute("error", "❌ Only JPEG, PNG, GIF, WebP images are allowed.");
                return "redirect:/admin/auction/create";
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
            // Admin bypass: can save even if auction has already started
            auctionService.save(auction, true);
            ra.addFlashAttribute("success", "Auction saved successfully!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/auction/create";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/admin/auctions";
    }

    @GetMapping("/auction/edit/{id}")
    public String editAuction(@PathVariable Long id,
            Model model, HttpSession session,
            RedirectAttributes ra) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);
        Auction auction = auctionService.getById(id);
        if (auction == null)
            return "redirect:/admin/auctions";
        // Admin can edit even started auctions — no isStarted() check
        model.addAttribute("auction", auction);
        return "admin/update_auction";
    }

    @PostMapping("/auction/delete/{id}")
    public String deleteAuction(@PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotAdmin(session))
            return "redirect:/login";
        // Admin bypass: can delete even started auctions
        String result = auctionService.adminDelete(id);
        if ("SUCCESS".equals(result)) {
            ra.addFlashAttribute("success", "Auction deleted.");
        } else {
            ra.addFlashAttribute("error", "❌ Auction not found.");
        }
        return "redirect:/admin/auctions";
    }

    @GetMapping("/auction/view/{id}")
    public String viewAuction(@PathVariable Long id,
            Model model, HttpSession session) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);
        Auction auction = auctionService.getById(id);
        if (auction == null)
            return "redirect:/admin/auctions";
        model.addAttribute("auction", auction);
        model.addAttribute("bids",
                auctionService.getBidsForAuction(id));
        return "admin/view_auction";
    }

    // ══════════════════════════════════════════════════
    // TENDER MANAGEMENT
    // ══════════════════════════════════════════════════
    @GetMapping("/tenders")
    public String listTenders(Model model, HttpSession session) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);
        model.addAttribute("tenders", tenderService.getAll());
        return "admin/list_tender";
    }

    @GetMapping("/tender/create")
    public String createTenderForm(Model model, HttpSession session) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);
        model.addAttribute("tender", new Tender());
        return "admin/create_tender";
    }

    @PostMapping("/tender/save")
    public String saveTender(@ModelAttribute Tender tender,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotAdmin(session))
            return "redirect:/login";
        // Set creator
        String email = (String) session.getAttribute("loggedInUser");
        if (email != null && !email.equals("admin")) {
            userRepository.findByEmail(email)
                    .ifPresent(tender::setCreatedBy);
        }

        try {
            // Admin bypass: can save even if tender has already started
            tenderService.save(tender, true);
            ra.addFlashAttribute("success", "Tender saved successfully!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/tender/create";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/admin/tenders";
    }

    @GetMapping("/tender/edit/{id}")
    public String editTender(@PathVariable Long id,
            Model model, HttpSession session,
            RedirectAttributes ra) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);
        Tender tender = tenderService.getById(id);
        if (tender == null)
            return "redirect:/admin/tenders";
        // Admin can edit even started tenders — no isStarted() check
        model.addAttribute("tender", tender);
        return "admin/update_tender";
    }

    @PostMapping("/tender/delete/{id}")
    public String deleteTender(@PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra) {
        if (isNotAdmin(session))
            return "redirect:/login";
        // Admin bypass: can delete even started tenders
        String result = tenderService.adminDelete(id);
        if ("SUCCESS".equals(result)) {
            ra.addFlashAttribute("success", "Tender deleted.");
        } else {
            ra.addFlashAttribute("error", "❌ Tender not found.");
        }
        return "redirect:/admin/tenders";
    }

    @GetMapping("/tender/view/{id}")
    public String viewTender(@PathVariable Long id,
            Model model, HttpSession session) {
        if (isNotAdmin(session))
            return "redirect:/login";
        addAdminMeta(model, session);
        Tender tender = tenderService.getById(id);
        if (tender == null)
            return "redirect:/admin/tenders";
        model.addAttribute("tender", tender);
        model.addAttribute("bids", tenderService.getBidsForTender(id));
        model.addAttribute("documents", tenderDocumentRepository.findByTenderId(id));
        return "admin/view_tender";
    }

    // ══════════════════════════════════════════════════
    // EXPORT MANAGEMENT
    // ══════════════════════════════════════════════════
    @GetMapping(value = "/export/tenders", produces = "text/csv")
    @ResponseBody
    public String exportTenders() {
        return adminService.generateTendersCsv();
    }

    @GetMapping(value = "/export/auctions", produces = "text/csv")
    @ResponseBody
    public String exportAuctions() {
        return adminService.generateAuctionsCsv();
    }

    @GetMapping(value = "/export/users", produces = "text/csv")
    @ResponseBody
    public String exportUsers() {
        return adminService.generateUsersCsv();
    }
}
