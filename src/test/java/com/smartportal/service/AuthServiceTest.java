package com.smartportal.service;

import com.smartportal.model.OtpToken;
import com.smartportal.model.User;
import com.smartportal.repository.OtpTokenRepository;
import com.smartportal.repository.UserRepository;
import com.smartportal.event.DomainEventPublisher;
import com.smartportal.util.HmacUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private OtpTokenRepository otpTokenRepository;
    @Mock private DomainEventPublisher events;
    @Mock private AuditService auditService;
    @Mock private LoginAttemptService loginAttemptService;
    @Spy private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        // Set admin credentials via reflection (normally injected by @Value)
        ReflectionTestUtils.setField(authService, "adminUsername", "admin");
        // L-1 FIX: admin password stored as BCrypt hash
        ReflectionTestUtils.setField(authService, "adminPassword",
                new BCryptPasswordEncoder().encode("Admin@2024"));
        ReflectionTestUtils.setField(authService, "appSecret", "test-secret-key-that-is-long-enough");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setMobile("9876543210");
        testUser.setRole("USER");
        testUser.setActive(true);
        testUser.setPassword(encoder.encode("Test@1234"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOGIN TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Login with valid user credentials returns role")
    void login_validCredentials_returnsRole() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        String result = authService.login("test@example.com", "Test@1234");
        assertEquals("USER", result);
        verify(userRepository).save(any(User.class)); // lastLogin updated
    }

    @Test
    @DisplayName("Login with wrong password returns null")
    void login_wrongPassword_returnsNull() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        assertNull(authService.login("test@example.com", "WrongPass"));
    }

    @Test
    @DisplayName("Login with non-existent email returns null")
    void login_unknownEmail_returnsNull() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());
        assertNull(authService.login("nobody@test.com", "anyPassword"));
    }

    @Test
    @DisplayName("Login with blocked user returns BLOCKED")
    void login_blockedUser_returnsBlocked() {
        testUser.setActive(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        assertEquals("BLOCKED", authService.login("test@example.com", "Test@1234"));
    }

    @Test
    @DisplayName("Admin login with valid credentials returns ADMIN")
    void login_adminCredentials_returnsAdmin() {
        assertEquals("ADMIN", authService.login("admin", "Admin@2024"));
    }

    @Test
    @DisplayName("Admin login is case-insensitive for username")
    void login_adminCaseInsensitive_returnsAdmin() {
        assertEquals("ADMIN", authService.login("ADMIN", "Admin@2024"));
    }

    @Test
    @DisplayName("Admin login with wrong password returns null")
    void login_adminWrongPassword_returnsNull() {
        assertNull(authService.login("admin", "wrongpass"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SIGNUP TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Signup with new user returns SUCCESS")
    void signup_newUser_returnsSuccess() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.findByMobile(any())).thenReturn(Optional.empty());
        assertEquals("SUCCESS", authService.signup(testUser));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Signup with duplicate email returns EMAIL_EXISTS")
    void signup_duplicateEmail_returnsEmailExists() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(testUser));
        assertEquals("EMAIL_EXISTS", authService.signup(testUser));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Signup with duplicate mobile returns MOBILE_EXISTS")
    void signup_duplicateMobile_returnsMobileExists() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.findByMobile(any())).thenReturn(Optional.of(testUser));
        assertEquals("MOBILE_EXISTS", authService.signup(testUser));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Signup stores BCrypt-encoded password")
    void signup_encodesPassword() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.findByMobile(any())).thenReturn(Optional.empty());
        testUser.setPassword("Plaintext@1");

        authService.signup(testUser);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNotEquals("Plaintext@1", captor.getValue().getPassword());
        assertTrue(encoder.matches("Plaintext@1", captor.getValue().getPassword()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OTP GENERATION TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Generate OTP for valid email returns SUCCESS")
    void generateOtp_validEmail_returnsSuccess() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        assertEquals("SUCCESS", authService.generateAndSendOtp("test@example.com"));
        verify(emailService).sendOtpEmail(eq("test@example.com"), eq("Test User"), anyString());
    }

    @Test
    @DisplayName("Generate OTP for unknown email returns NOT_FOUND")
    void generateOtp_unknownEmail_returnsNotFound() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", authService.generateAndSendOtp("nobody@test.com"));
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
    }

    @Test
    @DisplayName("Generate OTP sends 6-digit code")
    void generateOtp_sends6DigitCode() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        authService.generateAndSendOtp("test@example.com");

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpEmail(any(), any(), otpCaptor.capture());
        String otp = otpCaptor.getValue();

        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"), "OTP should be 6 digits: " + otp);
    }

    @Test
    @DisplayName("Generate OTP — email failure returns EMAIL_FAILED")
    void generateOtp_emailFailure_returnsEmailFailed() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        doThrow(new RuntimeException("SMTP error")).when(emailService)
                .sendOtpEmail(any(), any(), any());
        assertEquals("EMAIL_FAILED", authService.generateAndSendOtp("test@example.com"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OTP VERIFICATION TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Verify OTP — correct code returns SUCCESS")
    void verifyOtp_correctCode_returnsSuccess() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        authService.generateAndSendOtp("test@example.com");

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpEmail(eq("test@example.com"), eq("Test User"), otpCaptor.capture());
        String generatedOtp = otpCaptor.getValue();

        // Capture the OTP saved to DB
        ArgumentCaptor<OtpToken> tokenCaptor = ArgumentCaptor.forClass(OtpToken.class);
        verify(otpTokenRepository).save(tokenCaptor.capture());
        OtpToken savedToken = tokenCaptor.getValue();

        // Mock findByEmail to return the saved token
        when(otpTokenRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedToken));

        assertEquals("SUCCESS", authService.verifyOtp("test@example.com", generatedOtp));
    }

    @Test
    @DisplayName("Verify OTP — wrong code returns INVALID")
    void verifyOtp_wrongCode_returnsInvalid() {
        String secret = "test-secret-key-that-is-long-enough";
        OtpToken validToken = new OtpToken("test@example.com", HmacUtil.hmac("123456", secret),
                LocalDateTime.now().plusMinutes(10));
        when(otpTokenRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validToken));
        assertEquals("INVALID", authService.verifyOtp("test@example.com", "000000"));
    }

    @Test
    @DisplayName("Verify OTP — no pending OTP returns NOT_FOUND")
    void verifyOtp_noPendingOtp_returnsNotFound() {
        when(otpTokenRepository.findByEmail("never@requested.com")).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", authService.verifyOtp("never@requested.com", "123456"));
    }

    @Test
    @DisplayName("Verify OTP — consumed OTP cannot be reused")
    void verifyOtp_consumedOtp_cannotBeReused() {
        String secret = "test-secret-key-that-is-long-enough";
        OtpToken validToken = new OtpToken("test@example.com", HmacUtil.hmac("123456", secret),
                LocalDateTime.now().plusMinutes(10));
        // First call returns token, second call returns empty (consumed)
        when(otpTokenRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(validToken))
                .thenReturn(Optional.empty());

        // First use succeeds
        assertEquals("SUCCESS", authService.verifyOtp("test@example.com", "123456"));
        // Second use fails (consumed)
        assertEquals("NOT_FOUND", authService.verifyOtp("test@example.com", "123456"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PASSWORD RESET TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Reset password for valid email returns SUCCESS")
    void resetPassword_validEmail_returnsSuccess() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        assertEquals("SUCCESS", authService.resetPassword("test@example.com", "NewPass@123"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Reset password for unknown email returns NOT_FOUND")
    void resetPassword_unknownEmail_returnsNotFound() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", authService.resetPassword("nobody@test.com", "NewPass@123"));
    }

    @Test
    @DisplayName("Reset password stores BCrypt-encoded new password")
    void resetPassword_encodesNewPassword() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        authService.resetPassword("test@example.com", "NewSecurePass@1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(encoder.matches("NewSecurePass@1", captor.getValue().getPassword()));
    }

    @Test
    @DisplayName("Full flow: generate OTP → verify → reset password")
    void fullPasswordResetFlow() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // Step 1: Generate OTP
        assertEquals("SUCCESS", authService.generateAndSendOtp("test@example.com"));

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpEmail(eq("test@example.com"), eq("Test User"), otpCaptor.capture());
        String generatedOtp = otpCaptor.getValue();

        // Capture the OTP token saved to DB
        ArgumentCaptor<OtpToken> tokenCaptor = ArgumentCaptor.forClass(OtpToken.class);
        verify(otpTokenRepository).save(tokenCaptor.capture());
        OtpToken savedToken = tokenCaptor.getValue();

        // Mock findByEmail to return saved token for verification
        when(otpTokenRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedToken));

        // Step 2: Verify OTP
        assertEquals("SUCCESS", authService.verifyOtp("test@example.com", generatedOtp));

        // Step 3: Reset password
        assertEquals("SUCCESS", authService.resetPassword("test@example.com", "BrandNewPassword@1"));
    }

    @Test
    @DisplayName("Reset password with weak password returns WEAK_PASSWORD")
    void resetPassword_weakPassword_returnsWeakPassword() {
        assertEquals("WEAK_PASSWORD", authService.resetPassword("test@example.com", "abc"));
        verify(userRepository, never()).save(any());
    }
}
