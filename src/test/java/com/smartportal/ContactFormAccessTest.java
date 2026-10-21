package com.smartportal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContactFormAccessTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("GET /contact is publicly accessible")
    void getContact_anonymousAccess_returnsOk() throws Exception {
        mockMvc.perform(get("/contact"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /contact with CSRF is publicly accessible (not 403)")
    void postContact_anonymousWithCsrf_redirects() throws Exception {
        mockMvc.perform(post("/contact")
                        .with(csrf())
                        .param("name", "Test User")
                        .param("email", "test@example.com")
                        .param("subject", "General Inquiry")
                        .param("message", "Hello, this is a test message."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contact"));
    }

    @Test
    @DisplayName("POST /contact without CSRF returns 403")
    void postContact_anonymousNoCsrf_forbidden() throws Exception {
        mockMvc.perform(post("/contact")
                        .param("name", "Test User")
                        .param("email", "test@example.com")
                        .param("subject", "General Inquiry")
                        .param("message", "Hello, this is a test message."))
                .andExpect(status().isForbidden());
    }
}
