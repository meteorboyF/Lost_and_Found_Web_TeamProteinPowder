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
import org.springframework.stereotype.Component;

/* Jackson 3 lives under tools.jackson, not com.fasterxml.jackson. */
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.teamproteinpowder.lostfound.domain.Category;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.ItemKind;
import com.teamproteinpowder.lostfound.domain.ItemStatus;
import com.teamproteinpowder.lostfound.repo.ItemRepository;

/**
 * Populates an empty database from src/main/resources/seed/items.json so a
 * fresh clone shows a working board instead of an empty one.
 *
 * Runs only when the table is empty, so it never fights real data or
 * duplicates rows on restart.
 */
@Component
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final ItemRepository repository;
    private final ObjectMapper mapper;

    public SeedLoader(ItemRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
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
        item.setPhotoUrl(normalisePhoto(text(node, "image", null)));
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

    /** Seed rows point at bundled artwork under /assets, not at /uploads. */
    private static String normalisePhoto(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.startsWith("/") ? raw : "/" + raw;
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
}
