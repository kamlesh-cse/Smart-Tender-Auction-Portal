package com.smartportal.service;

import com.smartportal.exception.ForbiddenException;
import com.smartportal.model.User;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private static final String ADMIN = "ADMIN";
    private static final String GOVERNMENT = "GOVERNMENT";
    private static final String COMPANY = "COMPANY";
    private static final String USER = "USER";

    public boolean isAdmin(User user) {
        return hasRole(user, ADMIN);
    }

    public void enforceCanCreateTender(User actor) {
        if (isAdmin(actor) || hasRole(actor, GOVERNMENT) || hasRole(actor, COMPANY)) {
            return;
        }
        throw new ForbiddenException("Only admin, government, and company users can create tenders.");
    }

    public void enforceCanCreateAuction(User actor) {
        if (isAdmin(actor) || hasRole(actor, GOVERNMENT) || hasRole(actor, COMPANY)) {
            return;
        }
        throw new ForbiddenException("Only admin, government, and company users can create auctions.");
    }

    public void enforceCanParticipateInTender(User actor) {
        if (isAdmin(actor) || hasRole(actor, COMPANY)) {
            return;
        }
        throw new ForbiddenException("Only company users can participate in tenders.");
    }

    public void enforceCanParticipateInAuction(User actor) {
        if (isAdmin(actor) || hasRole(actor, COMPANY) || hasRole(actor, USER)) {
            return;
        }
        throw new ForbiddenException("Government users cannot participate in auctions.");
    }

    public void enforceCanEditOwnedResource(User actor, User creator, String resourceName) {
        if (isAdmin(actor) || sameUser(actor, creator)) {
            return;
        }
        throw new ForbiddenException("You can only edit your own " + resourceName + ".");
    }

    private boolean hasRole(User user, String role) {
        return user != null && role.equals(user.getRole());
    }

    private boolean sameUser(User actor, User creator) {
        return actor != null
                && creator != null
                && actor.getId() != null
                && actor.getId().equals(creator.getId());
    }
}
