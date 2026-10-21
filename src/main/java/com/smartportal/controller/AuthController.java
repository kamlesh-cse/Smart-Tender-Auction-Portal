package com.smartportal.controller;

import com.smartportal.model.User;
import com.smartportal.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;
import java.util.List;

@Controller
public class AuthController {

    private final AuthService authService;
    private final SessionRegistry sessionRegistry;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService, SessionRegistry sessionRegistry) {
        this.authService = authService;
        this.sessionRegistry = sessionRegistry;
    }

    // ── Login GET ──────────────────────────────────────
    @GetMapping("/login")
    public String showLogin() {
        return "login";
    }

    // ── Login POST ─────────────────────────────────────
    @PostMapping("/login")
    public String processLogin(@RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes ra) {

        email = email == null ? "" : email.trim();
        if (email.length() > 254 || password == null || password.length() > 128) {
            ra.addFlashAttribute("error", "Invalid login request.");
            return "redirect:/login";
        }
        String role = authService.login(email, password);

        if (role == null) {
            ra.addFlashAttribute("error", "❌ Invalid email or password. Please try again.");
            return "redirect:/login";
        }
        if ("BLOCKED".equals(role)) {
            ra.addFlashAttribute("error", "🚫 Your account has been blocked. Contact admin.");
            return "redirect:/login";
        }
        if ("LOCKED".equals(role)) {
            ra.addFlashAttribute("error", "Account temporarily locked. Try again in 15 minutes.");
            return "redirect:/login";
        }

        request.changeSessionId();
        session.setAttribute("loggedInUser", email);
        session.setAttribute("userRole", role);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, AuthorityUtils.createAuthorityList("ROLE_" + role)));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        sessionRegistry.getAllSessions(email, false).stream()
                .filter(existing -> !session.getId().equals(existing.getSessionId()))
                .forEach(org.springframework.security.core.session.SessionInformation::expireNow);
        sessionRegistry.registerNewSession(session.getId(), context.getAuthentication().getPrincipal());

        if (!"admin".equalsIgnoreCase(email)) {
            authService.getUserByEmail(email)
                    .ifPresent(u -> session.setAttribute("userName", u.getName()));
        } else {
            session.setAttribute("userName", "Admin");
        }

        switch (role) {
            case "ADMIN":
                return "redirect:/admin/dashboard";
            case "GOVERNMENT":
                return "redirect:/government/dashboard";
            case "COMPANY":
                return "redirect:/company/dashboard";
            default:
                return "redirect:/user/dashboard";
        }
    }

    // ── Signup GET ─────────────────────────────────────
    @GetMapping("/signup")
    public String showSignup(Model model) {
        model.addAttribute("user", new User());
        return "signup";
    }

    // ── Signup POST ────────────────────────────────────
    @PostMapping("/signup")
    public String processSignup(@Valid @ModelAttribute User user,
            BindingResult bindingResult,
            @RequestParam String role,
            RedirectAttributes ra) {

        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/signup";
        }

        List<String> allowedRoles = List.of("USER", "COMPANY", "GOVERNMENT");
        if (!allowedRoles.contains(role.toUpperCase())) {
            ra.addFlashAttribute("error", "Invalid role selected.");
            return "redirect:/signup";
        }

        user.setRole(role.toUpperCase());

        if ("USER".equalsIgnoreCase(role)) {
            user.setOrganisation(null);
        }

        if (("GOVERNMENT".equals(user.getRole()) || "COMPANY".equals(user.getRole()))
                && (user.getOrganisation() == null || user.getOrganisation().isBlank())) {
            ra.addFlashAttribute("error",
                    "Organisation name is required for Government and Company accounts.");
            return "redirect:/signup";
        }

        String result = authService.signup(user);

        switch (result) {
            case "WEAK_PASSWORD":
                ra.addFlashAttribute("error",
                        "Password must be at least 10 characters with upper, lower, number, and symbol.");
                return "redirect:/signup";
            case "EMAIL_EXISTS":
            case "MOBILE_EXISTS":
            default:
                ra.addFlashAttribute("success", "✅ Account created! Please login.");
                return "redirect:/login";
        }
    }

    // ── Logout ─────────────────────────────────────────
    @GetMapping("/logout")
    public String showLogout() {
        return "logout";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes ra) {
        sessionRegistry.removeSessionInformation(session.getId());
        SecurityContextHolder.clearContext();
        session.invalidate();
        ra.addFlashAttribute("success", "Logged out successfully.");
        return "redirect:/login";
    }

    // ══════════════════════════════════════════════════
    // FORGOT PASSWORD — OTP FLOW
    // ══════════════════════════════════════════════════

    // Step 1: Show email input form
    @GetMapping("/forgot-password")
    public String showForgotPassword() {
        return "forgot_password";
    }

    // Step 1 POST: Look up user, generate OTP, send to email
    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email,
            HttpSession session,
            RedirectAttributes ra) {
        String result = authService.generateAndSendOtp(email);
        switch (result) {
            case "EMAIL_FAILED":
                ra.addFlashAttribute("error",
                        "⚠️ Could not send OTP email. Please check that a valid Gmail App Password "
                                + "is set in application.properties (spring.mail.password).");
                return "redirect:/forgot-password";
            case "NOT_FOUND":
            default: // SUCCESS
                session.setAttribute("otpEmail", email);
                ra.addFlashAttribute("success",
                        "✅ A 6-digit OTP has been sent to " + email + ". Check your inbox (and spam folder).");
                return "redirect:/verify-otp?email=" + email;
        }
    }

    // Step 2: Show OTP entry form
    @GetMapping("/verify-otp")
    public String showVerifyOtp(@RequestParam(required = false) String email, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("otpEmail") == null) {
            if (email != null && !email.isBlank()) {
                session.setAttribute("otpEmail", email);
            } else {
                ra.addFlashAttribute("error", "Session expired. Please start again.");
                return "redirect:/forgot-password";
            }
        }
        return "verify_otp";
    }

    // Step 2 POST: Verify OTP
    @PostMapping("/verify-otp")
    public String processVerifyOtp(@RequestParam String otp,
            HttpSession session,
            RedirectAttributes ra) {
        String email = (String) session.getAttribute("otpEmail");
        if (email == null) {
            ra.addFlashAttribute("error", "Session expired. Please start again.");
            return "redirect:/forgot-password";
        }

        String result = authService.verifyOtp(email, otp.trim());
        switch (result) {
            case "EXPIRED":
                ra.addFlashAttribute("error", "⏱️ OTP has expired. Please request a new one.");
                session.removeAttribute("otpEmail");
                return "redirect:/forgot-password";
            case "INVALID":
                ra.addFlashAttribute("error", "❌ Incorrect OTP. Please try again.");
                return "redirect:/verify-otp";
            case "NOT_FOUND":
                ra.addFlashAttribute("error", "Session expired. Please start again.");
                return "redirect:/forgot-password";
            default: // SUCCESS
                session.setAttribute("resetEmail", email);
                session.removeAttribute("otpEmail");
                return "redirect:/reset-password";
        }
    }

    // Step 3: Show new-password form
    @GetMapping("/reset-password")
    public String showResetPassword(HttpSession session, Model model, RedirectAttributes ra) {
        String email = (String) session.getAttribute("resetEmail");
        if (email == null) {
            ra.addFlashAttribute("error", "Session expired. Please verify your OTP again.");
            return "redirect:/forgot-password";
        }
        model.addAttribute("verifiedEmail", email);
        return "reset_password";
    }

    // Step 3 POST: Save new password
    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String email,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession session,
            RedirectAttributes ra) {
        String sessionEmail = (String) session.getAttribute("resetEmail");
        if (sessionEmail == null || !sessionEmail.equals(email)) {
            ra.addFlashAttribute("error", "Session expired. Please start over.");
            return "redirect:/forgot-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("error", "❌ Passwords do not match.");
            return "redirect:/reset-password";
        }
        String result = authService.resetPassword(email, newPassword);
        switch (result) {
            // BUG-8 FIX: was "TOO_SHORT" — now WEAK_PASSWORD with correct requirement message
            case "WEAK_PASSWORD":
                ra.addFlashAttribute("error",
                        "❌ Password must be at least 10 characters with uppercase, lowercase, number, and symbol.");
                return "redirect:/reset-password";
            case "NOT_FOUND":
                ra.addFlashAttribute("error", "❌ Account not found.");
                return "redirect:/forgot-password";
            default: // SUCCESS
                session.removeAttribute("resetEmail");
                ra.addFlashAttribute("success",
                        "✅ Password reset successfully! Please login with your new password.");
                return "redirect:/login";
        }
    }
}
