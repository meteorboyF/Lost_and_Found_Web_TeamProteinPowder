package com.teamproteinpowder.lostfound.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.UserRepository;
import com.teamproteinpowder.lostfound.web.AuthController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Resolves the signed-in account from the session, if there is one.
 *
 * Content can be posted by a guest, so every caller treats the result as
 * optional: an absent user means the row keeps only its email, which is
 * exactly how everything worked before accounts existed.
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Optional<User> from(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object id = session.getAttribute(AuthController.SESSION_USER_ID);
        if (!(id instanceof Long userId)) {
            return Optional.empty();
        }
        return users.findById(userId);
    }
}
