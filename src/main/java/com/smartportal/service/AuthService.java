package com.smartportal.service;

import com.smartportal.model.OtpToken;
import com.smartportal.model.User;
import com.smartportal.event.DomainEventPublisher;
import com.smartportal.event.EmailNotificationRequested;
import com.smartportal.repository.OtpTokenRepository;
import com.smartportal.repository.UserRepository;
import com.smartportal.util.HmacUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final OtpTokenRepository otpTokenRepository;
    private final DomainEventPublisher events;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    // Admin credentials from application.properties
    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.secret}")
    private String appSecret;

    public AuthService(UserRepository userRepository, EmailService emailService,
                       OtpTokenRepository otpTokenRepository, DomainEventPublisher events,
                       AuditService auditService, PasswordEncoder passwordEncoder,
                       LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.otpTokenRepository = otpTokenRepository;
        this.events = events;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    // ── Login ──────────────────────────────────────────────────────────────────
    @Transactional
    public String login(String emailOrUsername, String password) {
        String loginId = emailOrUsername.toLowerCase(java.util.Locale.ROOT);
        if (loginAttemptService.isLocked(loginId)) {
            return "LOCKED";
        }

        // Admin login — supports both plain text and BCrypt-hashed passwords
        if (adminUsername.equalsIgnoreCase(emailOrUsername)) {
            if (passwordEncoder.matches(password, adminPassword)) {
                loginAttemptService.succeeded(loginId);
                auditService.record("ADMIN", "LOGIN", "SESSION", "username=" + emailOrUsername);
                return "ADMIN";
            }
            loginAttemptService.failed(loginId);
            return null;
        }

        Optional<User> optUser = userRepository.findByEmail(emailOrUsername);
        if (optUser.isEmpty()) {
            loginAttemptService.failed(loginId);
            return null;
        }

        User user = optUser.get();
        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(LocalDateTime.now()))
            return "LOCKED";

        // BUG-3 FIX: Check blocked BEFORE password to avoid info leak and give correct message
        if (!user.isActive())
            return "BLOCKED";

        if (!passwordEncoder.matches(password, user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 5) {
                user.setLockoutUntil(LocalDateTime.now().plusMinutes(15));
                user.setFailedLoginAttempts(0);
            }
            userRepository.save(user);
            loginAttemptService.failed(loginId);
            return null;
        }

        // Update last login
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        loginAttemptService.succeeded(loginId);
        auditService.record(String.valueOf(user.getId()), "LOGIN", "SESSION", "role=" + user.getRole());

        // Send login alert email (non-blocking — failure won't break login)
        try {
            String time = LocalDateTime.now().format(DISPLAY_FMT);
            events.publishAfterCommit(new EmailNotificationRequested(
                    "login:" + user.getId() + ":" + user.getLastLogin(),
                    "LOGIN", user.getEmail(), user.getName(), null, time,
                    null, user.getRole(), LocalDateTime.now()));
        } catch (Exception ignored) {
        }

        return user.getRole();
    }

    // ── Signup ─────────────────────────────────────────────────────────────────
    @Transactional
    public String signup(User user) {

        if (userRepository.findByEmail(user.getEmail()).isPresent() || userRepository.findByMobile(user.getMobile()).isPresent()) {
            passwordEncoder.encode(user.getPassword()); // Fake hash to prevent timing attack
            return userRepository.findByEmail(user.getEmail()).isPresent() ? "EMAIL_EXISTS" : "MOBILE_EXISTS";
        }
        if (!isStrongPassword(user.getPassword()))
            return "WEAK_PASSWORD";

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        auditService.record(String.valueOf(user.getId()), "CREATE", "USER:" + user.getId(), "role=" + user.getRole());

        // Send welcome email
        try {
            events.publishAfterCommit(new EmailNotificationRequested(
                    "signup:" + user.getId(), "SIGNUP", user.getEmail(), user.getName(),
                    null, null, null, user.getRole(), LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }

        return "SUCCESS";
    }

    // ── Get User By Email ──────────────────────────────────────────────────────
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // ── Forgot Password: generate and email OTP ────────────────────────────────
    // Returns: "NOT_FOUND" | "EMAIL_FAILED" | "SUCCESS"
    @Transactional
    public String generateAndSendOtp(String email) {
        Optional<User> optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) {
            passwordEncoder.encode("fake_password_to_prevent_timing_attack");
            return "NOT_FOUND";
        }

        User user = optUser.get();
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        // H-2 FIX: store OTP in database instead of in-memory map
        otpTokenRepository.deleteByEmail(email); // remove any previous OTP
        OtpToken token = new OtpToken(email, HmacUtil.hmac(otp, appSecret), LocalDateTime.now().plusMinutes(10));
        otpTokenRepository.save(token);

        try {
            emailService.sendOtpEmail(email, user.getName(), otp);
        } catch (Exception e) {
            log.error("[OTP EMAIL ERROR] {}", e.getMessage());
            otpTokenRepository.deleteByEmail(email);
            return "EMAIL_FAILED";
        }
        return "SUCCESS";
    }

    // ── Verify OTP ─────────────────────────────────────────────────────────────
    @Transactional
    public String verifyOtp(String email, String enteredOtp) {
        Optional<OtpToken> optToken = otpTokenRepository.findByEmail(email);
        if (optToken.isEmpty())
            return "NOT_FOUND";

        OtpToken token = optToken.get();
        if (token.isExpired()) {
            otpTokenRepository.deleteByEmail(email);
            return "EXPIRED";
        }
        if (!HmacUtil.matches(enteredOtp, appSecret, token.getOtp()))
            return "INVALID";

        otpTokenRepository.deleteByEmail(email); // consume OTP
        return "SUCCESS";
    }

    // ── Reset Password ───────────────────────────────────────────────────────
    @Transactional
    public String resetPassword(String email, String newPassword) {
        // BUG-8 FIX: Return WEAK_PASSWORD — check validates full strength, not just length
        if (!isStrongPassword(newPassword))
            return "WEAK_PASSWORD";
        Optional<User> optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty())
            return "NOT_FOUND";
        User user = optUser.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return "SUCCESS";
    }

    private boolean isStrongPassword(String password) {
        return password != null
                && password.length() >= 10
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9].*");
    }
}
