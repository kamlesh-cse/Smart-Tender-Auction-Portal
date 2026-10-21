package com.smartportal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ChatbotAuthTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("Anonymous GET /api/chatbot/csrf returns CSRF token (public endpoint)")
    void csrfEndpoint_anonymousAccess_returnsToken() throws Exception {
        mockMvc.perform(get("/api/chatbot/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Authenticated GET /api/chatbot/csrf returns CSRF token")
    void csrfEndpoint_authenticatedAccess_returnsToken() throws Exception {
        mockMvc.perform(get("/api/chatbot/csrf")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Anonymous POST /api/chatbot with CSRF succeeds (public support widget)")
    void chatPost_anonymousWithCsrf_succeeds() throws Exception {
        mockMvc.perform(post("/api/chatbot")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty());
    }

    @Test
    @DisplayName("Authenticated POST /api/chatbot with CSRF succeeds")
    void chatPost_authenticatedWithCsrf_succeeds() throws Exception {
        mockMvc.perform(post("/api/chatbot")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/chatbot without CSRF returns 403")
    void chatPost_noCsrf_forbidden() throws Exception {
        mockMvc.perform(post("/api/chatbot")
                        .contentType("application/json")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isForbidden());
    }
}
