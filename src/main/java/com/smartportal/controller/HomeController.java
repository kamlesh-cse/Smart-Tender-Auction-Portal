package com.smartportal.controller;

import com.smartportal.model.Auction;
import com.smartportal.model.Tender;
import com.smartportal.service.AuctionService;
import com.smartportal.service.TenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final AuctionService auctionService;
    private final TenderService tenderService;

    public HomeController(AuctionService auctionService,
            TenderService tenderService) {
        this.auctionService = auctionService;
        this.tenderService = tenderService;
    }

    // ── Home ───────────────────────────────────────────
    @GetMapping("/")
    public String home(Model model) {

        // FIX: Cache results — avoids double DB call for same data
        List<Auction> openAuctions = auctionService.getOpen();
        List<Tender> openTenders = tenderService.getOpen();

        model.addAttribute("totalAuctions", auctionService.getAll().size());
        model.addAttribute("openAuctions", openAuctions.size());
        model.addAttribute("totalTenders", tenderService.getAll().size());
        model.addAttribute("openTenders", openTenders.size());
        model.addAttribute("recentAuctions", openAuctions);
        model.addAttribute("recentTenders", openTenders);

        return "index";
    }

    // ── Public Auction List ────────────────────────────
    @GetMapping("/auctions")
    public String auctions(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Model model) {
        Page<Auction> result = auctionService.getOpen(PageRequest.of(
                Math.max(0, page), boundedPageSize(size), Sort.by("deadline").ascending()));
        model.addAttribute("auctions", result.getContent());
        model.addAttribute("page", result);
        return "auction";
    }

    // ── Public Tender List ─────────────────────────────
    @GetMapping("/tenders")
    public String tenders(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Model model) {
        Page<Tender> result = tenderService.getOpen(PageRequest.of(
                Math.max(0, page), boundedPageSize(size), Sort.by("deadline").ascending()));
        model.addAttribute("tenders", result.getContent());
        model.addAttribute("page", result);
        return "tender";
    }

    private int boundedPageSize(int requestedSize) {
        return Math.min(100, Math.max(1, requestedSize));
    }

    // ── About ──────────────────────────────────────────
    @GetMapping("/about")
    public String about() {
        return "about";
    }

    // ── Contact GET ────────────────────────────────────
    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    // ── Contact POST ───────────────────────────────────
    @PostMapping("/contact")
    public String submitContact(@RequestParam String name,
            @RequestParam String email,
            @RequestParam String subject,
            @RequestParam String message,
            RedirectAttributes ra) {

        if (name.isBlank() || name.length() > 100
                || email.length() > 254 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                || subject.isBlank() || subject.length() > 150
                || message.isBlank() || message.length() > 4000) {
            ra.addFlashAttribute("error", "Please provide valid contact details.");
            return "redirect:/contact";
        }

        log.info("Contact form — name={} | email={} | subject={}", name, email, subject);

        ra.addFlashAttribute("success",
                "Thank you " + name + "! We'll respond to " + email + " within 24 hours.");
        return "redirect:/contact";
    }
}
