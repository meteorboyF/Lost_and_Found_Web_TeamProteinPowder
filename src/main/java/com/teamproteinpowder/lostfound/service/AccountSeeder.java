package com.teamproteinpowder.lostfound.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.ItemRepository;
import com.teamproteinpowder.lostfound.repo.UserRepository;

/**
 * Gives bundled registry posts to an administrator. Other unlinked legacy
 * content requires a verified ownership migration by the operator: a guest
 * email string must never grant access to a private conversation on restart.
 */
@Component
@Order(20)
public class AccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);

    /** Seed items are posted by the registry desk; give them to an admin. */
    private static final String REGISTRY_EMAIL = "registry@example.edu";

    private final UserRepository users;
    private final ItemRepository items;

    public AccountSeeder(UserRepository users,
                         ItemRepository items) {
        this.users = users;
        this.items = items;
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
            // Guest email strings are not proof of account ownership.
            User owner = REGISTRY_EMAIL.equalsIgnoreCase(item.getReporterEmail())
                    && item.getReference().startsWith("LF-") ? admin : null;
            if (owner != null) {
                item.setUser(owner);
                items.save(item);
                linked++;
            }
        }

        if (linked > 0) {
            log.info("Linked {} rows to their accounts", linked);
        }
    }

}
