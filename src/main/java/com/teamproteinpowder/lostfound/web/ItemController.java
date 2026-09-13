package com.teamproteinpowder.lostfound.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.teamproteinpowder.lostfound.domain.Category;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.ItemKind;
import com.teamproteinpowder.lostfound.domain.ItemStatus;
import com.teamproteinpowder.lostfound.service.CurrentUser;
import com.teamproteinpowder.lostfound.service.ItemService;
import com.teamproteinpowder.lostfound.service.MatchService;
import com.teamproteinpowder.lostfound.service.StorageService;
import com.teamproteinpowder.lostfound.web.dto.ItemRequest;
import com.teamproteinpowder.lostfound.web.dto.ItemResponse;
import com.teamproteinpowder.lostfound.web.dto.MatchResponse;
import com.teamproteinpowder.lostfound.web.dto.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService items;
    private final StorageService storage;
    private final MatchService matches;
    private final CurrentUser currentUser;

    public ItemController(ItemService items, StorageService storage, MatchService matches,
                          CurrentUser currentUser) {
        this.items = items;
        this.storage = storage;
        this.matches = matches;
        this.currentUser = currentUser;
    }

    /** Browse and search. Every filter is optional. */
    @GetMapping
    public PageResponse<ItemResponse> list(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "recent") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "12") int size) {

        return PageResponse.of(
                items.search(
                        parse(ItemKind.class, kind, "kind"),
                        parse(ItemStatus.class, status, "status"),
                        parse(Category.class, category, "category"),
                        q, sort, page, size),
                ItemResponse::from);
    }

    @GetMapping("/{reference}")
    public ItemResponse one(@PathVariable String reference) {
        return ItemResponse.from(items.getByReference(reference));
    }

    /**
     * Create a post. Accepts multipart so the photograph arrives with the rest
     * of the form in one request; the JSON part is bound and validated exactly
     * as it would be on a plain JSON endpoint.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ItemResponse> create(
            @RequestPart("item") @Valid @Validated ItemRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            HttpServletRequest httpRequest) {

        request.setPhotoUrl(storage.store(photo));
        Item saved = items.create(request);
        /* Link the post to its author when one is signed in; a guest post
           simply keeps its email and stays unlinked. */
        currentUser.from(httpRequest).ifPresent(user -> items.attachOwner(saved, user));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ItemResponse.from(saved));
    }

    /** Likely matches on the opposite side of the board. */
    @GetMapping("/{reference}/matches")
    public List<MatchResponse> matches(@PathVariable String reference) {
        return matches.findMatches(items.getByReference(reference))
                .stream().map(MatchResponse::from).toList();
    }

    /** Board-wide counts for the landing page. */
    @GetMapping("/stats")
    public ItemService.Stats stats() {
        return items.stats();
    }

    /** The category list, so the frontend never hard-codes the enum. */
    @GetMapping("/categories")
    public List<Map<String, String>> categories() {
        return Arrays.stream(Category.values())
                .map(c -> Map.of("value", c.name(), "label", c.getLabel()))
                .toList();
    }

    /**
     * Turns a query-string value into an enum constant, or a 400 naming the
     * legal values — a typo in a filter should say so, not silently return
     * everything.
     */
    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown " + field + " '" + raw + "'. Expected one of "
                            + Arrays.toString(type.getEnumConstants()));
        }
    }
}
