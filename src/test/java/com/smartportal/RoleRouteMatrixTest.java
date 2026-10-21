package com.smartportal;

import com.smartportal.model.User;
import com.smartportal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoleRouteMatrixTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String TEST_EMAIL = "matrix-test@example.com";

    @BeforeEach
    void ensureTestUser() {
        if (userRepository.findByEmail(TEST_EMAIL).isEmpty()) {
            User u = new User();
            u.setName("Matrix Test User");
            u.setEmail(TEST_EMAIL);
            u.setMobile("7777777777");
            u.setPassword(passwordEncoder.encode("Password@1234"));
            u.setRole("USER"); // default, overridden per-test via mock
            u.setActive(true);
            userRepository.save(u);
        }
    }

    // ── Each role can access its own dashboard ──────────────────────────────

    @Test
    @DisplayName("ADMIN can access /admin/dashboard")
    void adminDashboard_isOk() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("admin").roles("ADMIN"))
                        .sessionAttr("loggedInUser", "admin")
                        .sessionAttr("userRole", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GOVERNMENT can access /government/dashboard")
    void govDashboard_isOk() throws Exception {
        ensureUser("gov-matrix@test.com", "GOVERNMENT");
        mockMvc.perform(get("/government/dashboard")
                        .with(user("gov-matrix@test.com").roles("GOVERNMENT"))
                        .sessionAttr("loggedInUser", "gov-matrix@test.com")
                        .sessionAttr("userRole", "GOVERNMENT"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("COMPANY can access /company/dashboard")
    void companyDashboard_isOk() throws Exception {
        ensureUser("company-matrix@test.com", "COMPANY");
        mockMvc.perform(get("/company/dashboard")
                        .with(user("company-matrix@test.com").roles("COMPANY"))
                        .sessionAttr("loggedInUser", "company-matrix@test.com")
                        .sessionAttr("userRole", "COMPANY"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER can access /user/dashboard")
    void userDashboard_isOk() throws Exception {
        mockMvc.perform(get("/user/dashboard")
                        .with(user(TEST_EMAIL).roles("USER"))
                        .sessionAttr("loggedInUser", TEST_EMAIL)
                        .sessionAttr("userRole", "USER"))
                .andExpect(status().isOk());
    }

    // ── Cross-role access is denied ─────────────────────────────────────────

    @ParameterizedTest(name = "{0} cannot access {1}")
    @DisplayName("Cross-role access is denied by Spring Security")
    @CsvSource({
            "USER,        /admin/dashboard",
            "USER,        /government/dashboard",
            "USER,        /company/dashboard",
            "COMPANY,     /admin/dashboard",
            "COMPANY,     /government/dashboard",
            "COMPANY,     /user/dashboard",
            "GOVERNMENT,  /admin/dashboard",
            "GOVERNMENT,  /company/dashboard",
            "GOVERNMENT,  /user/dashboard",
            "ADMIN,       /government/dashboard",
            "ADMIN,       /company/dashboard",
            "ADMIN,       /user/dashboard"
    })
    void crossRoleAccess_isForbidden(String role, String path) throws Exception {
        mockMvc.perform(get(path)
                        .with(user("attacker@test.com").roles(role)))
                .andExpect(status().isForbidden());
    }

    // ── Anonymous access to role routes is forbidden ────────────────────────

    @ParameterizedTest(name = "Anonymous cannot access {0}")
    @DisplayName("Anonymous access to role-prefixed routes is denied")
    @CsvSource({
            "/admin/dashboard",
            "/government/dashboard",
            "/company/dashboard",
            "/user/dashboard"
    })
    void anonymousAccess_isForbidden(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isForbidden());
    }

    private void ensureUser(String email, String role) {
        if (userRepository.findByEmail(email).isEmpty()) {
            User u = new User();
            u.setName("Test " + role);
            u.setEmail(email);
            u.setMobile("888" + String.valueOf(email.hashCode()).replaceAll("-", "").substring(0, 7));
            u.setPassword(passwordEncoder.encode("Password@1234"));
            u.setRole(role);
            u.setActive(true);
            userRepository.save(u);
        }
    }
}
