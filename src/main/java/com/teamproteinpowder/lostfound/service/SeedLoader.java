package com.teamproteinpowder.lostfound.service;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/* Jackson 3 lives under tools.jackson, not com.fasterxml.jackson. */
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.teamproteinpowder.lostfound.domain.ApprovalStatus;
import com.teamproteinpowder.lostfound.domain.Category;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.ItemKind;
import com.teamproteinpowder.lostfound.domain.ItemStatus;
import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.ItemRepository;
import com.teamproteinpowder.lostfound.repo.UserRepository;

/**
 * Populates an empty database from src/main/resources/seed/items.json so a
 * fresh clone shows a working board instead of an empty one.
 *
 * Runs only when the table is empty, so it never fights real data or
 * duplicates rows on restart.
 */
@Component
@Order(10)   // items first; AccountSeeder (20) links them to accounts
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final ItemRepository repository;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final ObjectMapper mapper;
    private final boolean demoEnabled;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    public SeedLoader(ItemRepository repository,
                      UserRepository userRepository,
                      PasswordService passwordService,
                      ObjectMapper mapper,
                      @org.springframework.beans.factory.annotation.Value("${app.demo.enabled:false}") boolean demoEnabled,
                      @org.springframework.beans.factory.annotation.Value("${app.bootstrap.admin-email:}") String bootstrapEmail,
                      @org.springframework.beans.factory.annotation.Value("${app.bootstrap.admin-password:}") String bootstrapPassword) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.mapper = mapper;
        this.demoEnabled = demoEnabled;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (demoEnabled) seedUsers();
        else bootstrapAdmin();
        warnIfNoAdmin();

        if (repository.count() > 0) {
            log.info("Database already has {} items — skipping seed", repository.count());
            return;
        }

        ClassPathResource resource = new ClassPathResource("seed/items.json");
        if (!resource.exists()) {
            log.warn("No seed/items.json on the classpath — starting with an empty board");
            return;
        }

        try (InputStream in = resource.getInputStream()) {
            JsonNode root = mapper.readTree(in);
            JsonNode array = root.has("items") ? root.get("items") : root;

            List<Item> batch = new java.util.ArrayList<>();
            int index = 0;
            for (JsonNode node : array) {
                batch.add(toItem(node, index++));
            }
            repository.saveAll(batch);
            log.info("Seeded {} items", batch.size());
        }
    }

    private Item toItem(JsonNode node, int index) {
        Item item = new Item();

        item.setReference(text(node, "id", "LF-SEED-" + index));
        item.setKind("lost".equalsIgnoreCase(text(node, "kind", "found"))
                ? ItemKind.LOST
                : ItemKind.FOUND);
        item.setStatus(mapStatus(text(node, "status", "open")));
        item.setCategory(mapCategory(text(node, "category", "Other")));
        item.setTitle(text(node, "title", "Untitled item"));
        item.setDescription(text(node, "description", ""));
        item.setColour(text(node, "colour", null));
        item.setLocation(buildLocation(node));
        /* Seed rows deliberately carry no photograph. A missing photo renders
           as a tinted panel with the category glyph, which reads as a designed
           placeholder; the prototype's faint line-art SVGs read as a broken
           image on a light card. Real uploads still show real photographs. */
        item.setPhotoUrl(null);
        item.setReporterName("Registry desk");
        item.setReporterEmail("registry@example.edu");

        Instant reported = parseInstant(text(node, "reportedAt", null), index);
        item.setCreatedAt(reported);
        item.setUpdatedAt(reported);
        item.setHappenedOn(LocalDate.ofInstant(reported, ZoneOffset.UTC));

        return item;
    }

    /**
     * The prototype fixtures used a nine-state registry lifecycle. This build
     * has three, so the historical states collapse onto their nearest
     * equivalent rather than being dropped.
     */
    private static ItemStatus mapStatus(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "returned", "resolved", "verified" -> ItemStatus.RESOLVED;
            case "claim-pending", "pending", "match-suggested", "disputed" -> ItemStatus.PENDING;
            default -> ItemStatus.OPEN;
        };
    }

    private static Category mapCategory(String raw) {
        String key = raw.toLowerCase(Locale.ROOT);
        if (key.contains("electronic") || key.contains("phone") || key.contains("laptop")) {
            return Category.ELECTRONICS;
        }
        if (key.contains("id") || key.contains("card") || key.contains("document")) {
            return Category.ID_CARDS;
        }
        if (key.contains("key")) {
            return Category.KEYS;
        }
        if (key.contains("bag") || key.contains("luggage") || key.contains("backpack")) {
            return Category.BAGS;
        }
        if (key.contains("cloth") || key.contains("jacket") || key.contains("scarf")) {
            return Category.CLOTHING;
        }
        if (key.contains("book") || key.contains("stationery") || key.contains("notebook")) {
            return Category.BOOKS;
        }
        if (key.contains("jewel") || key.contains("ring") || key.contains("watch")) {
            return Category.JEWELLERY;
        }
        return Category.OTHER;
    }

    private static String buildLocation(JsonNode node) {
        String building = text(node, "buildingName", null);
        String spot = text(node, "location", null);
        if (building != null && spot != null) {
            return building + " — " + spot;
        }
        if (building != null) {
            return building;
        }
        return spot == null ? "Campus" : spot;
    }

    private static Instant parseInstant(String raw, int index) {
        if (raw != null && !raw.isBlank()) {
            try {
                return Instant.parse(raw);
            } catch (Exception ignored) {
                // fall through to the staggered default below
            }
        }
        /* Stagger the fallbacks so "most recent" ordering stays meaningful
           even when a fixture row has no usable timestamp. */
        return Instant.now().minus(index + 1L, ChronoUnit.HOURS);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String s = value.asString("").trim();
        return s.isEmpty() ? fallback : s;
    }

    private void seedUsers() {
        // Ensure Admin user
        userRepository.findByEmailIgnoreCase("admin@campus.edu").ifPresentOrElse(admin -> {
            // Existing accounts and approval decisions are never rewritten on restart.
        }, () -> {
            String adminSalt = passwordService.generateSalt();
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@campus.edu");
            admin.setStudentEmail("admin@campus.edu");
            admin.setStudentId("ADMIN-001");
            admin.setSalt(adminSalt);
            admin.setPasswordHash(passwordService.hashPassword("admin123", adminSalt));
            admin.setRole(Role.ADMIN);
            admin.setApprovalStatus(ApprovalStatus.APPROVED);
            userRepository.save(admin);
        });

        // Ensure Approved Student user
        userRepository.findByEmailIgnoreCase("student@campus.edu").ifPresentOrElse(student -> {
            // Preserve administrator decisions on existing accounts.
        }, () -> {
            String studentSalt = passwordService.generateSalt();
            User student = new User();
            student.setUsername("student");
            student.setEmail("student@campus.edu");
            student.setStudentEmail("student@campus.edu");
            student.setStudentId("STU-2026-001");
            student.setSalt(studentSalt);
            student.setPasswordHash(passwordService.hashPassword("student123", studentSalt));
            student.setRole(Role.USER);
            student.setApprovalStatus(ApprovalStatus.APPROVED);
            userRepository.save(student);
        });

        // Ensure Pending Student user for testing admin approval queue
        if (userRepository.findByEmailIgnoreCase("pending_student@campus.edu").isEmpty()) {
            String pendingSalt = passwordService.generateSalt();
            User pending = new User();
            pending.setUsername("pending_student");
            pending.setEmail("pending_student@campus.edu");
            pending.setStudentEmail("pending_student@campus.edu");
            pending.setStudentId("STU-2026-999");
            pending.setSalt(pendingSalt);
            pending.setPasswordHash(passwordService.hashPassword("student123", pendingSalt));
            pending.setRole(Role.USER);
            pending.setApprovalStatus(ApprovalStatus.PENDING);
            userRepository.save(pending);
        }

        log.info("Seeded initial users with student credentials and verification statuses");
    }

    /**
     * Every signup waits for an admin to approve it, so a database without an
     * active admin is stuck: nobody can sign in and nobody can be approved.
     * Say so loudly at startup, with the fix, instead of failing silently.
     */
    private void warnIfNoAdmin() {
        if (userRepository.countByRoleAndApprovalStatus(Role.ADMIN, ApprovalStatus.APPROVED) > 0) return;
        log.warn("No active administrator account exists, so new signups cannot be approved. "
                + "Local development: start with APP_DEMO_ENABLED=true (./run.sh does this by default) "
                + "to create admin@campus.edu / admin123. Production: set APP_BOOTSTRAP_ADMIN_EMAIL and "
                + "APP_BOOTSTRAP_ADMIN_PASSWORD (12+ characters) and restart.");
    }

    private void bootstrapAdmin() {
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) return;
        if (bootstrapPassword.length() < 12) throw new IllegalStateException("Bootstrap admin password must be at least 12 characters");
        String email = bootstrapEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) return;
        User admin = new User();
        admin.setUsername("registry-admin");
        admin.setEmail(email);
        admin.setStudentEmail(email);
        admin.setStudentId("BOOTSTRAP-ADMIN");
        admin.setRole(Role.ADMIN);
        admin.setApprovalStatus(ApprovalStatus.APPROVED);
        admin.setSalt(passwordService.generateSalt());
        admin.setPasswordHash(passwordService.hashPassword(bootstrapPassword, admin.getSalt()));
        userRepository.save(admin);
        log.info("Created configured registry administrator");
    }
}
