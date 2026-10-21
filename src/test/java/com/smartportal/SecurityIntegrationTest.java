package com.smartportal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void anonymousCannotOpenAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    void wrongRoleCannotOpenAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginPageRemainsPublic() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/login").param("email", "user@example.com").param("password", "Password@123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/logout").with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCanObtainChatbotCsrfToken() throws Exception {
        mockMvc.perform(get("/api/chatbot/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void authenticatedUserCanObtainChatbotCsrfToken() throws Exception {
        mockMvc.perform(get("/api/chatbot/csrf").with(user("user@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void chatbotPostRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/chatbot").contentType("application/json").content("{\"message\":\"hello\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedChatbotPostWithCsrfReturnsResponse() throws Exception {
        mockMvc.perform(post("/api/chatbot")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.reply").isNotEmpty())
                .andExpect(jsonPath("$.suggestions").isArray());
    }
}