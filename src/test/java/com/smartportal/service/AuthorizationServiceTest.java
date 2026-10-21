package com.smartportal.service;

import com.smartportal.exception.ForbiddenException;
import com.smartportal.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {

    private AuthorizationService authService;

    private User admin;
    private User government;
    private User company;
    private User normalUser;

    @BeforeEach
    void setUp() {
        authService = new AuthorizationService();

        admin = new User();
        admin.setId(1L);
        admin.setRole("ADMIN");

        government = new User();
        government.setId(2L);
        government.setRole("GOVERNMENT");

        company = new User();
        company.setId(3L);
        company.setRole("COMPANY");

        normalUser = new User();
        normalUser.setId(4L);
        normalUser.setRole("USER");
    }

    @Test
    @DisplayName("isAdmin returns true only for ADMIN role")
    void isAdmin_checksRoleCorrectly() {
        assertTrue(authService.isAdmin(admin));
        assertFalse(authService.isAdmin(government));
        assertFalse(authService.isAdmin(company));
        assertFalse(authService.isAdmin(normalUser));
        assertFalse(authService.isAdmin(null));
    }

    @Test
    @DisplayName("enforceCanCreateTender allows allowed roles")
    void enforceCanCreateTender_allowsAllowedRoles() {
        assertDoesNotThrow(() -> authService.enforceCanCreateTender(admin));
        assertDoesNotThrow(() -> authService.enforceCanCreateTender(government));
        assertDoesNotThrow(() -> authService.enforceCanCreateTender(company));
        
        assertThrows(ForbiddenException.class, () -> authService.enforceCanCreateTender(normalUser));
        assertThrows(ForbiddenException.class, () -> authService.enforceCanCreateTender(null));
    }

    @Test
    @DisplayName("enforceCanCreateAuction allows allowed roles")
    void enforceCanCreateAuction_allowsAllowedRoles() {
        assertDoesNotThrow(() -> authService.enforceCanCreateAuction(admin));
        assertDoesNotThrow(() -> authService.enforceCanCreateAuction(government));
        assertDoesNotThrow(() -> authService.enforceCanCreateAuction(company));
        
        assertThrows(ForbiddenException.class, () -> authService.enforceCanCreateAuction(normalUser));
        assertThrows(ForbiddenException.class, () -> authService.enforceCanCreateAuction(null));
    }

    @Test
    @DisplayName("enforceCanParticipateInTender allows COMPANY and ADMIN")
    void enforceCanParticipateInTender_allowsCompanyAndAdmin() {
        assertDoesNotThrow(() -> authService.enforceCanParticipateInTender(admin));
        assertDoesNotThrow(() -> authService.enforceCanParticipateInTender(company));
        
        assertThrows(ForbiddenException.class, () -> authService.enforceCanParticipateInTender(government));
        assertThrows(ForbiddenException.class, () -> authService.enforceCanParticipateInTender(normalUser));
        assertThrows(ForbiddenException.class, () -> authService.enforceCanParticipateInTender(null));
    }

    @Test
    @DisplayName("enforceCanParticipateInAuction allows USER, COMPANY, ADMIN")
    void enforceCanParticipateInAuction_allowsAllowedRoles() {
        assertDoesNotThrow(() -> authService.enforceCanParticipateInAuction(admin));
        assertDoesNotThrow(() -> authService.enforceCanParticipateInAuction(company));
        assertDoesNotThrow(() -> authService.enforceCanParticipateInAuction(normalUser));
        
        assertThrows(ForbiddenException.class, () -> authService.enforceCanParticipateInAuction(government));
        assertThrows(ForbiddenException.class, () -> authService.enforceCanParticipateInAuction(null));
    }

    @Test
    @DisplayName("enforceCanEditOwnedResource allows creator or ADMIN")
    void enforceCanEditOwnedResource_allowsCreatorOrAdmin() {
        User creator = new User();
        creator.setId(10L);
        
        User someoneElse = new User();
        someoneElse.setId(20L);
        
        // ADMIN can edit anything
        assertDoesNotThrow(() -> authService.enforceCanEditOwnedResource(admin, creator, "resource"));
        
        // Creator can edit their own resource
        assertDoesNotThrow(() -> authService.enforceCanEditOwnedResource(creator, creator, "resource"));
        
        // Someone else cannot edit
        assertThrows(ForbiddenException.class, () -> authService.enforceCanEditOwnedResource(someoneElse, creator, "resource"));
        
        // Null checks
        assertThrows(ForbiddenException.class, () -> authService.enforceCanEditOwnedResource(null, creator, "resource"));
        assertThrows(ForbiddenException.class, () -> authService.enforceCanEditOwnedResource(creator, null, "resource"));
    }
}
