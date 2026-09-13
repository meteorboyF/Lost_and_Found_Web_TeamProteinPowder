package com.teamproteinpowder.lostfound.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamproteinpowder.lostfound.domain.Claim;
import com.teamproteinpowder.lostfound.domain.Comment;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.ClaimRepository;
import com.teamproteinpowder.lostfound.repo.CommentRepository;
import com.teamproteinpowder.lostfound.repo.ItemRepository;
import com.teamproteinpowder.lostfound.repo.UserRepository;

/**
 * Attaches existing content to the account that created it.
 *
 * Items, claims, and comments predate user accounts, so they record a person
 * as a name and an email string rather than a foreign key. That left `users`
 * as an island in the schema: no query could answer "what has this person
 * posted?". This backfills the {@code user_id} added to those three tables by
 * matching the stored email against a registered account.
 *
 * Accounts themselves are created by {@link SeedLoader}; this runs after it
 * (order 20 vs 10) and never creates a user, so there is exactly one place
 * that owns demo credentials.
 *
 * A row whose email belongs to no account stays unlinked, which is correct —
 * that is a guest post, not a data error.
 */
@Component
@Order(20)
public class AccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);

    /** Seed items are posted by the registry desk; give them to an admin. */
    private static final String REGISTRY_EMAIL = "registry@example.edu";

    private final UserRepository users;
    private final ItemRepository items;
    private final ClaimRepository claims;
    private final CommentRepository comments;

    public AccountSeeder(UserRepository users,
                         ItemRepository items,
                         ClaimRepository claims,
                         CommentRepository comments) {
        this.users = users;
        this.items = items;
        this.claims = claims;
        this.comments = comments;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> everyone = users.findAll();
        if (everyone.isEmpty()) {
            return;
        }

        User admin = everyone.stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst()
                .orElse(null);

        int linked = 0;

        for (Item item : items.findAll()) {
            if (item.getUser() != null) {
                continue;
            }
            User owner = match(everyone, item.getReporterEmail());
            if (owner == null && REGISTRY_EMAIL.equalsIgnoreCase(item.getReporterEmail())) {
                owner = admin;
            }
            if (owner != null) {
                item.setUser(owner);
                items.save(item);
                linked++;
            }
        }

        for (Claim claim : claims.findAll()) {
            if (claim.getUser() != null) {
                continue;
            }
            User owner = match(everyone, claim.getClaimantEmail());
            if (owner != null) {
                claim.setUser(owner);
                claims.save(claim);
                linked++;
            }
        }

        for (Comment comment : comments.findAll()) {
            if (comment.getUser() != null) {
                continue;
            }
            User owner = match(everyone, comment.getAuthorEmail());
            if (owner != null) {
                comment.setUser(owner);
                comments.save(comment);
                linked++;
            }
        }

        if (linked > 0) {
            log.info("Linked {} rows to their accounts", linked);
        }
    }

    private static User match(List<User> everyone, String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return everyone.stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail())
                          || email.equalsIgnoreCase(u.getStudentEmail()))
                .findFirst()
                .orElse(null);
    }
}
