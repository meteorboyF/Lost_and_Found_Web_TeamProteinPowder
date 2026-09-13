package com.teamproteinpowder.lostfound.service;

import java.security.SecureRandom;
import java.time.Year;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.teamproteinpowder.lostfound.domain.Category;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.ItemKind;
import com.teamproteinpowder.lostfound.domain.ItemStatus;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.ItemRepository;
import com.teamproteinpowder.lostfound.web.dto.ItemRequest;

@Service
public class ItemService {

    /** No I, O, 0, or 1 — reference codes get read aloud and written down. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int MAX_PAGE_SIZE = 60;

    private final ItemRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ItemService(ItemRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Item create(ItemRequest request) {
        Item item = new Item();
        item.setReference(nextReference());
        item.setKind(request.getKind());
        item.setStatus(ItemStatus.OPEN);
        item.setCategory(request.getCategory());
        item.setTitle(request.getTitle().trim());
        item.setDescription(request.getDescription().trim());
        item.setColour(blankToNull(request.getColour()));
        item.setLocation(request.getLocation().trim());
        item.setLatitude(request.getLatitude());
        item.setLongitude(request.getLongitude());
        item.setHappenedOn(request.getHappenedOn());
        item.setPhotoUrl(blankToNull(request.getPhotoUrl()));
        item.setReporterName(request.getReporterName().trim());
        item.setReporterEmail(request.getReporterEmail().trim().toLowerCase());
        return repository.save(item);
    }

    @Transactional(readOnly = true)
    public Page<Item> search(ItemKind kind, ItemStatus status, Category category,
                             String query, String sort, int page, int size) {

        String q = blankToNull(query);
        Sort order = switch (sort == null ? "recent" : sort) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "title" -> Sort.by(Sort.Direction.ASC, "title");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        return repository.search(kind, status, category, q,
                PageRequest.of(safePage, safeSize, order));
    }

    /** Record which account posted an item, once it is known. */
    @Transactional
    public Item attachOwner(Item item, User user) {
        item.setUser(user);
        return repository.save(item);
    }

    @Transactional(readOnly = true)
    public Item getByReference(String reference) {
        return repository.findByReference(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No item with reference " + reference));
    }

    @Transactional(readOnly = true)
    public Item getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No item with id " + id));
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        return new Stats(
                repository.countByStatus(ItemStatus.OPEN),
                repository.countByStatus(ItemStatus.PENDING),
                repository.countByStatus(ItemStatus.RESOLVED),
                repository.countByKind(ItemKind.LOST),
                repository.countByKind(ItemKind.FOUND),
                repository.count());
    }

    public record Stats(long open, long pending, long resolved,
                        long lost, long found, long total) {
    }

    /**
     * Reference codes look like LF-2026-K7Q3. The random suffix is re-rolled on
     * the vanishingly rare collision rather than trusting first-try uniqueness.
     */
    private String nextReference() {
        String year = String.valueOf(Year.now().getValue());
        for (int attempt = 0; attempt < 12; attempt++) {
            StringBuilder suffix = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                suffix.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            }
            String candidate = "LF-" + year + "-" + suffix;
            if (!repository.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique reference code");
    }

    private static String blankToNull(String value) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }
}
