package com.smartportal.service;

import com.smartportal.dto.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatbotService {

    // ── FAQ Knowledge Base ──────────────────────────────────────────────────
    // Each entry: keywords → { answer, relatedSuggestions }
    private record FaqEntry(Set<String> keywords, String answer, List<String> suggestions) {}

    private final List<FaqEntry> knowledgeBase = new ArrayList<>();

    public ChatbotService() {
        initKnowledgeBase();
    }

    // ── Public API ──────────────────────────────────────────────────────────
    public ChatResponse getResponse(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new ChatResponse(
                "Please type a question and I'll do my best to help! 😊",
                getDefaultSuggestions()
            );
        }

        String normalized = userMessage.toLowerCase().trim()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ");
        Set<String> words = new HashSet<>(Arrays.asList(normalized.split(" ")));

        // Score each FAQ entry by keyword overlap
        FaqEntry bestMatch = null;
        int bestScore = 0;

        for (FaqEntry entry : knowledgeBase) {
            int score = 0;
            for (String keyword : entry.keywords) {
                if (keyword.contains(" ")) {
                    // Multi-word phrase → check if input contains it
                    if (normalized.contains(keyword)) score += 3;
                } else {
                    if (words.contains(keyword)) score += 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestMatch = entry;
            }
        }

        // Greeting detection
        if (isGreeting(normalized)) {
            return new ChatResponse(
                "Hello! 👋 Welcome to SmartPortal Support.\n\nI can help you with:\n• Auctions & Bidding\n• Tenders & Proposals\n• Account & Login\n• Platform Navigation\n\nWhat would you like to know?",
                getDefaultSuggestions()
            );
        }

        if (bestScore >= 2 && bestMatch != null) {
            return new ChatResponse(bestMatch.answer, bestMatch.suggestions);
        } else if (bestScore == 1 && bestMatch != null) {
            return new ChatResponse(
                bestMatch.answer + "\n\n💡 _Not quite what you meant? Try rephrasing or pick a topic below._",
                bestMatch.suggestions
            );
        }

        // Fallback
        return new ChatResponse(
            "I'm sorry, I couldn't find an answer to that. 🤔\n\nHere are some topics I can help with:\n• How to sign up or log in\n• How auctions and bidding work\n• How tenders and proposals work\n• Password reset help\n• Understanding user roles\n\nTry clicking one of the suggestions below!",
            getDefaultSuggestions()
        );
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private boolean isGreeting(String text) {
        Set<String> greetings = Set.of("hi", "hello", "hey", "hola", "good morning",
                "good evening", "good afternoon", "sup", "yo", "greetings", "namaste");
        for (String g : greetings) {
            if (text.equals(g) || text.startsWith(g + " ")) return true;
        }
        return false;
    }

    private List<String> getDefaultSuggestions() {
        return List.of("How do I sign up?", "How to bid on an auction?",
                "How to fill a tender?", "What are the user roles?");
    }

    // ── Knowledge Base Initialization ───────────────────────────────────────
    private void initKnowledgeBase() {

        // ━━━ ACCOUNT & AUTH ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        faq(Set.of("sign up", "signup", "register", "create", "account"),
            "📝 **How to Sign Up**\n\n" +
            "1. Click **Sign Up** in the top-right corner\n" +
            "2. Fill in your name, email, mobile, and password\n" +
            "3. Choose your role: **User**, **Company**, or **Government**\n" +
            "4. Click **Register**\n\n" +
            "You'll receive a welcome email and can log in immediately.",
            List.of("What are the user roles?", "How to log in?", "How to reset password?"));

        faq(Set.of("log in", "login", "sign in", "signin"),
            "🔑 **How to Log In**\n\n" +
            "1. Go to the **Login** page\n" +
            "2. Enter your registered email and password\n" +
            "3. Click **Login**\n\n" +
            "You'll be redirected to your role-specific dashboard. " +
            "A login alert email will be sent to your inbox.",
            List.of("How to sign up?", "Forgot my password", "What are the user roles?"));

        faq(Set.of("password", "forgot", "reset", "otp"),
            "🔐 **Password Reset (OTP)**\n\n" +
            "1. Click **Forgot Password?** on the login page\n" +
            "2. Enter your registered email address\n" +
            "3. Check your inbox for a **6-digit OTP** (valid for 10 minutes)\n" +
            "4. Enter the OTP on the verification page\n" +
            "5. Set your new password (minimum 6 characters)\n\n" +
            "⚠️ Check your spam folder if the email doesn't arrive.",
            List.of("How to log in?", "OTP not received?", "How to sign up?"));

        faq(Set.of("otp", "not", "received", "email", "spam"),
            "📧 **OTP Not Received?**\n\n" +
            "• Check your **spam/junk** folder\n" +
            "• Make sure you entered the correct email address\n" +
            "• OTPs expire after **10 minutes** — request a new one if expired\n" +
            "• If the problem persists, contact support via the **Contact** page",
            List.of("How to reset password?", "How to contact support?"));

        faq(Set.of("role", "roles", "user", "company", "government", "admin", "types"),
            "👥 **User Roles on SmartPortal**\n\n" +
            "• **User** — Browse and bid on public auctions\n" +
            "• **Company** — Bid on auctions, fill tender proposals, upload documents\n" +
            "• **Government** — Create and manage auctions & tenders\n" +
            "• **Admin** — Full platform control, user management, all CRUD operations\n\n" +
            "You choose your role during sign-up (except Admin).",
            List.of("How to sign up?", "How to bid on an auction?", "How to create a tender?"));

        faq(Set.of("profile", "edit", "update", "name", "email", "mobile"),
            "✏️ **Profile Updates**\n\n" +
            "Currently, profile editing is managed by the Admin. " +
            "Contact the admin through the **Contact** page to request profile changes.\n\n" +
            "Your dashboard shows your current account details.",
            List.of("How to contact support?", "What are the user roles?"));

        // ━━━ AUCTIONS ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        faq(Set.of("auction", "what", "auctions", "how", "work"),
            "🏛️ **How Auctions Work**\n\n" +
            "• **Auctions** are created by Government users or Admins\n" +
            "• They have a **start date**, **time**, **duration**, and **base price**\n" +
            "• Users and Companies can place bids above the current highest bid\n" +
            "• When the deadline passes, the **highest bidder wins** automatically\n" +
            "• Winner and loser notifications are sent by email",
            List.of("How to bid on an auction?", "How to create an auction?", "What happens when auction closes?"));

        faq(Set.of("bid", "place bid", "bidding", "auction"),
            "💰 **How to Bid on an Auction**\n\n" +
            "1. Log in as **User** or **Company**\n" +
            "2. Go to **Auctions** → click **View Details** on an open auction\n" +
            "3. Enter a bid amount **higher than the current highest bid**\n" +
            "4. Click **Place Bid**\n\n" +
            "⚠️ Bids are **final** and cannot be withdrawn.\n" +
            "📧 You'll receive a confirmation email after bidding.",
            List.of("Can I bid on my own auction?", "What if my bid is too low?", "How auctions work?"));

        faq(Set.of("bid", "low", "minimum", "rejected", "too low"),
            "⛔ **Bid Too Low?**\n\n" +
            "Your bid must be **higher than the current highest bid**.\n\n" +
            "If no bids exist yet, your bid must be **at least the base price** set by the auction creator.\n\n" +
            "Check the auction details page for the current highest bid amount.",
            List.of("How to bid on an auction?", "How auctions work?"));

        faq(Set.of("own", "bid", "auction", "myself"),
            "🚫 **Can I Bid on My Own Auction?**\n\n" +
            "No, you cannot place a bid on an auction that you created. " +
            "This rule ensures fair competition among bidders.",
            List.of("How to bid on an auction?", "How to create an auction?"));

        faq(Set.of("create", "auction", "new"),
            "🆕 **Creating an Auction**\n\n" +
            "Only **Government** and **Admin** users can create auctions:\n\n" +
            "1. Go to your dashboard → **Create Auction**\n" +
            "2. Fill in: title, description, category, base price\n" +
            "3. Set the auction date, time, and duration (in minutes)\n" +
            "4. Optionally upload an image\n" +
            "5. Click **Submit**\n\n" +
            "The system calculates the deadline automatically from date + time + duration.",
            List.of("How auctions work?", "How to edit an auction?", "How to delete an auction?"));

        faq(Set.of("auction", "close", "closes", "winner", "end", "expired", "result"),
            "🏆 **When an Auction Closes**\n\n" +
            "• The system **automatically closes** auctions when the deadline passes\n" +
            "• The **highest bidder** is declared the winner\n" +
            "• Both winner and other bidders receive **email notifications**\n" +
            "• Results are visible on the auction details page\n\n" +
            "The auto-close check runs every **60 seconds**.",
            List.of("How to bid on an auction?", "How to create an auction?"));

        faq(Set.of("edit", "update", "modify", "auction"),
            "✏️ **Editing an Auction**\n\n" +
            "The auction creator (Government/Admin) can edit auction details " +
            "from their dashboard → **Auctions** → **Edit** button.\n\n" +
            "⚠️ Be careful editing auctions that already have bids.",
            List.of("How to create an auction?", "How to delete an auction?"));

        faq(Set.of("delete", "remove", "auction"),
            "🗑️ **Deleting an Auction**\n\n" +
            "Government and Admin users can delete auctions from the auction list.\n\n" +
            "• All associated **bids are also deleted** automatically\n" +
            "• This action is **permanent** and cannot be undone",
            List.of("How to create an auction?", "How to edit an auction?"));

        // ━━━ TENDERS ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        faq(Set.of("tender", "tenders", "what", "how", "work"),
            "📋 **How Tenders Work**\n\n" +
            "• **Tenders** are procurement requests created by Government users or Admins\n" +
            "• They have a **start date**, **end date**, **budget**, and **category**\n" +
            "• **Company** users can submit proposals with a quoted amount and documents\n" +
            "• When the tender closes, the creator selects a winner\n" +
            "• Minimum tender duration is **7 days**",
            List.of("How to fill a tender?", "How to create a tender?", "What documents to upload?"));

        faq(Set.of("fill", "tender", "submit", "proposal", "apply"),
            "📤 **How to Fill / Submit a Tender Proposal**\n\n" +
            "1. Log in as **Company**\n" +
            "2. Go to **Tenders** → click **View Details** on an open tender\n" +
            "3. Enter your **quoted amount** and **proposal description**\n" +
            "4. Optionally upload a **supporting document** (PDF, etc.)\n" +
            "5. Click **Submit Proposal**\n\n" +
            "📧 You'll receive a confirmation email. You can only submit once per tender.",
            List.of("How tenders work?", "What documents to upload?", "Can I edit my proposal?"));

        faq(Set.of("create", "tender", "new"),
            "🆕 **Creating a Tender**\n\n" +
            "Only **Government** and **Admin** users can create tenders:\n\n" +
            "1. Go to your dashboard → **Create Tender**\n" +
            "2. Fill in: title, description, category, estimated budget\n" +
            "3. Set start and end dates (must be at least **7 days apart**)\n" +
            "4. Click **Submit**",
            List.of("How tenders work?", "How to fill a tender?"));

        faq(Set.of("document", "upload", "file", "pdf", "attachment"),
            "📎 **Document Uploads**\n\n" +
            "When filling a tender proposal, you can upload a supporting document:\n" +
            "• Supported formats: PDF, DOC, DOCX\n" +
            "• Maximum file size depends on server config\n" +
            "• Documents are stored securely on the server\n\n" +
            "Auction images can also be uploaded during auction creation.",
            List.of("How to fill a tender?", "How to create an auction?"));

        faq(Set.of("tender", "close", "result", "winner"),
            "🏆 **Tender Results**\n\n" +
            "• Tenders are **automatically closed** when the end date passes\n" +
            "• The system selects the **lowest bidder** as the tender winner\n" +
            "• Winner and other bidders receive **email notifications**\n" +
            "• Results are visible on the tender details page\n\n" +
            "The auto-close check runs every **5 minutes**.",
            List.of("How to fill a tender?", "How tenders work?"));

        // ━━━ DASHBOARD & NAVIGATION ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        faq(Set.of("dashboard", "home", "my", "page"),
            "🏠 **Your Dashboard**\n\n" +
            "After logging in, you're taken to your role-specific dashboard:\n\n" +
            "• **User**: View open auctions, your bids, won/lost counts\n" +
            "• **Company**: Auctions, tenders, your bids and proposals\n" +
            "• **Government**: Manage your created auctions & tenders\n" +
            "• **Admin**: Full platform overview with all stats",
            List.of("What are the user roles?", "How to bid on an auction?", "How to fill a tender?"));

        faq(Set.of("my", "bids", "bid history", "participated"),
            "📊 **Your Bid History**\n\n" +
            "View all your bids from your dashboard:\n" +
            "• **User**: Dashboard → **My Bids** section\n" +
            "• **Company**: Dashboard → **My Bids** (auctions + tenders)\n\n" +
            "Each entry shows the auction/tender title, your bid amount, and result status.",
            List.of("How to bid on an auction?", "How to fill a tender?"));

        // ━━━ GENERAL / SUPPORT ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        faq(Set.of("what", "smartportal", "about", "portal", "platform"),
            "🏛️ **About SmartPortal**\n\n" +
            "SmartPortal is a **Smart Tender & Auction Portal** for transparent, " +
            "digital public procurement.\n\n" +
            "• Government agencies publish auctions and tenders\n" +
            "• Companies and individuals bid competitively\n" +
            "• Results are declared automatically and transparently\n" +
            "• Email notifications keep everyone informed",
            List.of("What are the user roles?", "How auctions work?", "How tenders work?"));

        faq(Set.of("contact", "support", "help", "reach", "phone", "email"),
            "📞 **Contact Support**\n\n" +
            "• **Email**: support@smartportal.in\n" +
            "• **Phone**: +91 98765 43210 (Mon–Sat, 9 AM – 6 PM IST)\n" +
            "• **Office**: Block A, Tech Hub, Connaught Place, New Delhi – 110001\n\n" +
            "You can also use the **Contact** page to send a message directly.",
            List.of("What is SmartPortal?", "How to sign up?"));

        faq(Set.of("withdraw", "cancel", "bid", "undo"),
            "⚠️ **Can I Withdraw a Bid?**\n\n" +
            "No, bids are **final and cannot be withdrawn** once placed.\n\n" +
            "Please review the auction details carefully before placing a bid.",
            List.of("How to bid on an auction?", "How auctions work?"));

        faq(Set.of("security", "safe", "data", "privacy"),
            "🔒 **Security & Privacy**\n\n" +
            "• Passwords are encrypted with **BCrypt** hashing\n" +
            "• Password reset uses **time-limited OTP** (10-minute expiry)\n" +
            "• Login alerts are sent to your email for security\n" +
            "• Your data is stored securely in an encrypted database",
            List.of("How to reset password?", "How to log in?"));

        faq(Set.of("thank", "thanks", "bye", "goodbye"),
            "You're welcome! 😊 Happy to help.\n\n" +
            "If you have more questions, feel free to ask anytime. Good luck with your auctions and tenders! 🎉",
            getDefaultSuggestions());
    }

    private void faq(Set<String> keywords, String answer, List<String> suggestions) {
        knowledgeBase.add(new FaqEntry(keywords, answer, suggestions));
    }
}
