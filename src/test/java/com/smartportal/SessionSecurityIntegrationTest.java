package com.smartportal;

import com.smartportal.service.AuthService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@AutoConfigureMockMvc
class SessionSecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired SessionRegistry sessionRegistry;
    @MockBean AuthService authService;

    @Test
    void loginRotatesSessionId() throws Exception {
        when(authService.login("user@example.com", "Password@123")).thenReturn("USER");
        when(authService.getUserByEmail("user@example.com")).thenReturn(Optional.empty());
        MockHttpSession original = new MockHttpSession();
        String originalId = original.getId();

        MvcResult result = mockMvc.perform(post("/login")
                        .session(original)
                        .with(csrf())
                        .param("email", "user@example.com")
                        .param("password", "Password@123"))
                .andExpect(redirectedUrl("/user/dashboard"))
                .andReturn();

        assertNotEquals(originalId, result.getRequest().getSession().getId());
    }

    @Test
    void secondLoginExpiresPreviousSessionForSameUser() throws Exception {
        when(authService.login("user@example.com", "Password@123")).thenReturn("USER");
        when(authService.getUserByEmail("user@example.com")).thenReturn(Optional.empty());

        MvcResult first = login();
        MvcResult second = login();

        String firstId = first.getRequest().getSession().getId();
        String secondId = second.getRequest().getSession().getId();
        assertTrue(sessionRegistry.getSessionInformation(firstId).isExpired());
        assertTrue(!sessionRegistry.getSessionInformation(secondId).isExpired());
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/login")
                        .session(new MockHttpSession())
                        .with(csrf())
                        .param("email", "user@example.com")
                        .param("password", "Password@123"))
                .andExpect(redirectedUrl("/user/dashboard"))
                .andReturn();
    }
}
