package com.smartportal.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${server.port:8080}")
    private int serverPort;

    private String baseUrl() {
        return "http://localhost:" + serverPort;
    }

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── Core Send ──────────────────────────────────────────────────────────────
    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Smart Tender Portal");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("[EMAIL OK] Sent to {} | Subject: {}", to, subject);
        } catch (Exception e) {
            // Print the full cause chain so the SMTP error is visible in the console
            Throwable root = e;
            while (root.getCause() != null)
                root = root.getCause();
            // BUG-16 FIX: use SLF4J logger instead of System.err
            log.error("[EMAIL FAILED] To: {} | Type: {} | Message: {} | Root: {}",
                    to, e.getClass().getSimpleName(), e.getMessage(), root.getMessage());
            // Rethrow so callers (e.g. generateAndSendOtp) can detect the failure
            throw new RuntimeException("Email send failed: " + root.getMessage(), e);
        }
    }

    // ── HTML Wrapper ───────────────────────────────────────────────────────────
    private String template(String icon, String heading, String body) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<style>body{font-family:Inter,sans-serif;background:#f4f6fb;margin:0;padding:30px;}"
                + ".card{max-width:540px;margin:auto;background:#fff;border-radius:16px;"
                + "box-shadow:0 4px 24px rgba(0,0,0,.08);overflow:hidden;}"
                + ".header{background:linear-gradient(135deg,#4e73df,#224abe);padding:32px;text-align:center;}"
                + ".header .icon{font-size:3rem;}"
                + ".header h1{color:#fff;font-size:1.2rem;margin:10px 0 0;font-weight:700;}"
                + ".body{padding:32px 36px;color:#333;font-size:0.93rem;line-height:1.7;}"
                + ".body h2{color:#1a1c23;font-size:1.05rem;margin:0 0 12px;}"
                + ".info-box{background:#f0f4ff;border-radius:10px;padding:14px 18px;margin:16px 0;"
                + "border-left:4px solid #4e73df;font-size:0.88rem;}"
                + ".btn{display:inline-block;background:linear-gradient(135deg,#4e73df,#224abe);"
                + "color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;"
                + "font-weight:600;margin:16px 0;font-size:0.9rem;}"
                + ".footer{background:#f8f9fc;border-top:1px solid #eee;padding:18px 36px;"
                + "font-size:0.78rem;color:#aaa;text-align:center;}"
                + "</style></head><body>"
                + "<div class='card'>"
                + "<div class='header'><div class='icon'>" + icon + "</div>"
                + "<h1>Smart Tender &amp; Auction Portal</h1></div>"
                + "<div class='body'><h2>" + heading + "</h2>" + body + "</div>"
                + "<div class='footer'>This is an automated notification. Please do not reply to this email.<br>"
                + "&copy; 2026 Smart Tender Portal. All rights reserved.</div>"
                + "</div></body></html>";
    }

    // ── 1. Welcome / Signup Email ──────────────────────────────────────────────
    public void sendSignupEmail(String to, String name, String role) {
        String roleLabel = switch (role.toUpperCase()) {
            case "GOVERNMENT" -> "🏛️ Government";
            case "COMPANY" -> "🏢 Company";
            default -> "👤 User";
        };
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
                + "<p>Welcome to <strong>Smart Tender &amp; Auction Portal</strong>! Your account has been successfully created.</p>"
                + "<div class='info-box'>"
                + "📧 <strong>Email:</strong> " + to + "<br>"
                + "👤 <strong>Role:</strong> " + roleLabel
                + "</div>"
                + "<p>You can now log in and start exploring open auctions and tenders.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>Go to Login →</a>"
                + "<p style='color:#888;font-size:0.82rem;margin-top:20px;'>If you did not create this account, please ignore this email.</p>";
        send(to, "🎉 Welcome to Smart Tender Portal, " + name + "!",
                template("🎉", "Account Created Successfully", body));
    }

    // ── 2. Login Alert Email ──────────────────────────────────────────────────
    public void sendLoginEmail(String to, String name, String loginTime) {
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
                + "<p>We detected a new login to your Smart Tender Portal account.</p>"
                + "<div class='info-box'>"
                + "🕐 <strong>Time:</strong> " + loginTime + "<br>"
                + "📧 <strong>Account:</strong> " + to
                + "</div>"
                + "<p>If this was you, no action is needed. If you did <strong>not</strong> log in, please reset your password immediately.</p>"
                + "<a class='btn' href='" + baseUrl() + "/forgot-password'>Reset Password →</a>";
        send(to, "🔐 New Login to Your Smart Portal Account", template("🔐", "Login Alert", body));
    }

    // ── 3. Auction Created Email ───────────────────────────────────────────────
    public void sendAuctionCreatedEmail(String to, String name, String auctionTitle, String deadline) {
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
                + "<p>Your auction has been published successfully on Smart Tender Portal!</p>"
                + "<div class='info-box'>"
                + "🏷️ <strong>Auction:</strong> " + auctionTitle + "<br>"
                + "⏰ <strong>Deadline:</strong> " + deadline
                + "</div>"
                + "<p>Participants can now discover and bid on your auction. You'll be notified when bids are placed.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>View Auction →</a>";
        send(to, "✅ Auction Published: " + auctionTitle, template("🔨", "Auction Created", body));
    }

    // ── 4. Tender Created Email ────────────────────────────────────────────────
    public void sendTenderCreatedEmail(String to, String name, String tenderTitle, String deadline) {
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
                + "<p>Your tender has been published successfully on Smart Tender Portal!</p>"
                + "<div class='info-box'>"
                + "📋 <strong>Tender:</strong> " + tenderTitle + "<br>"
                + "⏰ <strong>Deadline:</strong> " + deadline
                + "</div>"
                + "<p>Companies can now submit proposals for your tender. You'll be notified when proposals arrive.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>View Tender →</a>";
        send(to, "✅ Tender Published: " + tenderTitle, template("📋", "Tender Created", body));
    }

    // ── 5. Bid Placed — Confirmation to Bidder ───────────────────────────────
    public void sendBidPlacedEmail(String to, String bidderName, String auctionTitle, double amount) {
        String body = "<p>Hi <strong>" + bidderName + "</strong>,</p>"
                + "<p>Your bid has been placed successfully!</p>"
                + "<div class='info-box'>"
                + "🔨 <strong>Auction:</strong> " + auctionTitle + "<br>"
                + "💰 <strong>Your Bid:</strong> ₹" + String.format("%.2f", amount)
                + "</div>"
                + "<p>You are currently the highest bidder. We'll notify you if someone outbids you.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>View Auction →</a>";
        send(to, "🔨 Bid Placed: " + auctionTitle, template("🔨", "Bid Placed Successfully", body));
    }

    // ── 6. Bid Alert — to Auction Creator ─────────────────────────────────────
    public void sendBidAlertToCreator(String to, String creatorName, String auctionTitle, String bidderName,
            double amount) {
        String body = "<p>Hi <strong>" + creatorName + "</strong>,</p>"
                + "<p>A new bid has been placed on your auction!</p>"
                + "<div class='info-box'>"
                + "🔨 <strong>Auction:</strong> " + auctionTitle + "<br>"
                + "👤 <strong>Bidder:</strong> " + bidderName + "<br>"
                + "💰 <strong>Bid Amount:</strong> ₹" + String.format("%.2f", amount)
                + "</div>"
                + "<p>This is now the highest bid on your auction.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>View Bids →</a>";
        send(to, "💰 New Bid on Your Auction: " + auctionTitle, template("💰", "New Bid Received", body));
    }

    // ── 7. Tender Proposal — Confirmation to Company ──────────────────────────
    public void sendTenderSubmittedEmail(String to, String companyName, String tenderTitle, double amount) {
        String body = "<p>Hi <strong>" + companyName + "</strong>,</p>"
                + "<p>Your proposal has been submitted successfully!</p>"
                + "<div class='info-box'>"
                + "📋 <strong>Tender:</strong> " + tenderTitle + "<br>"
                + "💰 <strong>Your Bid Amount:</strong> ₹" + String.format("%.2f", amount)
                + "</div>"
                + "<p>The tender owner will review all proposals after the deadline. You'll be notified of the result.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>View Tender →</a>";
        send(to, "📋 Proposal Submitted: " + tenderTitle, template("📋", "Tender Proposal Submitted", body));
    }

    // ── 8. Tender Alert — to Tender Creator ───────────────────────────────────
    public void sendTenderAlertToCreator(String to, String creatorName, String tenderTitle, String companyName,
            double amount) {
        String body = "<p>Hi <strong>" + creatorName + "</strong>,</p>"
                + "<p>A new proposal has been submitted for your tender!</p>"
                + "<div class='info-box'>"
                + "📋 <strong>Tender:</strong> " + tenderTitle + "<br>"
                + "🏢 <strong>Company:</strong> " + companyName + "<br>"
                + "💰 <strong>Proposed Amount:</strong> ₹" + String.format("%.2f", amount)
                + "</div>"
                + "<p>You can review all proposals on your tender management page.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>Review Proposals →</a>";
        send(to, "📥 New Proposal on Your Tender: " + tenderTitle, template("📥", "New Proposal Received", body));
    }

    // ── 9. Auction Winner Email ────────────────────────────────────────────────
    public void sendAuctionWinnerEmail(String to, String name, String auctionTitle, Double winningBid) {
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
                + "<p>🎉 Congratulations! You have won the auction!</p>"
                + "<div class='info-box'>"
                + "🔨 <strong>Auction:</strong> " + auctionTitle + "<br>"
                + "🏆 <strong>Winning Bid:</strong> ₹" + String.format("%.2f", winningBid)
                + "</div>"
                + "<p>The auction owner will contact you with further details about the transaction.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>View Details →</a>";
        send(to, "🏆 You Won the Auction: " + auctionTitle, template("🏆", "Auction Won!", body));
    }

    // ── 10. Tender Winner Email ────────────────────────────────────────────────
    public void sendTenderWinnerEmail(String to, String name, String tenderTitle, Double winningBid) {
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
                + "<p>🎉 Congratulations! Your proposal has been selected!</p>"
                + "<div class='info-box'>"
                + "📋 <strong>Tender:</strong> " + tenderTitle + "<br>"
                + "🏆 <strong>Winning Amount:</strong> ₹" + String.format("%.2f", winningBid)
                + "</div>"
                + "<p>The tender owner will contact you with further details to proceed.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>View Details →</a>";
        send(to, "🏆 Your Proposal Won: " + tenderTitle, template("🏆", "Tender Proposal Selected!", body));
    }

    // ── 11. Result Notification to Creator ────────────────────────────────────
    public void sendResultToCreator(String to, String name, String title, String winnerName, Double winningAmount,
            String type) {
        String icon = "AUCTION".equals(type) ? "🔨" : "📋";
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
                + "<p>The result for your " + type.toLowerCase() + " has been declared!</p>"
                + "<div class='info-box'>"
                + icon + " <strong>" + type + ":</strong> " + title + "<br>"
                + "🏆 <strong>Winner:</strong> " + winnerName + "<br>"
                + "💰 <strong>Winning Amount:</strong> ₹" + String.format("%.2f", winningAmount)
                + "</div>"
                + "<a class='btn' href='" + baseUrl() + "/login'>View Result →</a>";
        send(to, "📊 Result Declared: " + title, template("📊", type + " Result Declared", body));
    }

    // ── 12. Loser Notification ─────────────────────────────────────────────────
    public void sendLoserNotification(String to, String name, String title, String type) {
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
                + "<p>Thank you for participating in <strong>" + title + "</strong>.</p>"
                + "<p>Unfortunately, you were not selected as the winner this time. We encourage you to participate in other open "
                + type.toLowerCase() + "s on the portal.</p>"
                + "<a class='btn' href='" + baseUrl() + "/login'>Browse Open " + type + "s →</a>";
        send(to, "Thank you for participating: " + title, template("📬", "Participation Result", body));
    }

    // ── 13. OTP for Forgot Password ───────────────────────────────────────────
    public void sendOtpEmail(String to, String name, String otp) {
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
                + "<p>You requested a password reset for your Smart Tender Portal account.</p>"
                + "<p style='text-align:center;'>"
                + "<span style='font-size:2.5rem;font-weight:800;letter-spacing:10px;"
                + "background:#f0f4ff;padding:16px 30px;border-radius:12px;border:2px dashed #4e73df;"
                + "display:inline-block;color:#224abe;font-family:monospace;'>"
                + otp + "</span></p>"
                + "<div class='info-box'>⏱️ This OTP is valid for <strong>10 minutes</strong> only.<br>"
                + "❌ Do NOT share this OTP with anyone.</div>"
                + "<p>If you did not request a password reset, please ignore this email — your account is safe.</p>";
        send(to, "🔑 Your Password Reset OTP", template("🔑", "Password Reset OTP", body));
    }
}