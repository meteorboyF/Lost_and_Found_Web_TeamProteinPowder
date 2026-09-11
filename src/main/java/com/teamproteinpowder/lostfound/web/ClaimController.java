package com.teamproteinpowder.lostfound.web;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamproteinpowder.lostfound.domain.Claim;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.service.ClaimService;
import com.teamproteinpowder.lostfound.service.ItemService;
import com.teamproteinpowder.lostfound.web.dto.ClaimRequest;
import com.teamproteinpowder.lostfound.web.dto.ClaimResponse;
import com.teamproteinpowder.lostfound.web.dto.MessageRequest;

import jakarta.validation.Valid;

/**
 * Claims and their conversations.
 *
 * NOTE ON ACCESS: until sign-in exists, a claim's reference code is also its
 * access key — whoever holds the code can read and reply to that thread.
 * Codes are 4 characters from a 32-symbol alphabet, so they are not guessable
 * in bulk, but this is deliberately a stopgap and is replaced by real
 * authentication before this goes anywhere near production.
 */
@RestController
@RequestMapping("/api")
public class ClaimController {

    private final ClaimService claims;
    private final ItemService items;

    public ClaimController(ClaimService claims, ItemService items) {
        this.claims = claims;
        this.items = items;
    }

    /** Open a claim on an item. */
    @PostMapping("/items/{reference}/claims")
    public ResponseEntity<ClaimResponse> create(@PathVariable String reference,
                                                @Valid @RequestBody ClaimRequest request) {
        Item item = items.getByReference(reference);
        Claim claim = claims.create(item, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClaimResponse.from(claim));
    }

    /** Every claim on an item — what the poster sees on their dashboard. */
    @GetMapping("/items/{reference}/claims")
    public List<ClaimResponse> forItem(@PathVariable String reference) {
        Item item = items.getByReference(reference);
        return claims.forItem(item).stream().map(ClaimResponse::summary).toList();
    }

    /** One conversation. */
    @GetMapping("/claims/{reference}")
    public ClaimResponse one(@PathVariable String reference) {
        return ClaimResponse.from(claims.getDetail(reference));
    }

    /**
     * Bulk lookup by reference, so the dashboard can resolve the codes held in
     * the browser's local storage in a single request.
     */
    @GetMapping("/claims")
    public List<ClaimResponse> byReferences(@RequestParam(required = false) String refs,
                                            @RequestParam(required = false) String email) {
        if (email != null && !email.isBlank()) {
            return claims.involving(email).stream().map(ClaimResponse::summary).toList();
        }
        if (refs == null || refs.isBlank()) {
            return List.of();
        }
        List<String> list = Arrays.stream(refs.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(100)
                .toList();
        return claims.byReferences(list).stream().map(ClaimResponse::summary).toList();
    }

    @PostMapping("/claims/{reference}/messages")
    public ClaimResponse.MessageResponse reply(@PathVariable String reference,
                                               @Valid @RequestBody MessageRequest request) {
        return ClaimResponse.MessageResponse.from(
                claims.reply(reference, request.getBody(), request.isFromPoster()));
    }

    @PostMapping("/claims/{reference}/accept")
    public ClaimResponse accept(@PathVariable String reference) {
        claims.accept(reference);
        return ClaimResponse.from(claims.getDetail(reference));
    }

    @PostMapping("/claims/{reference}/decline")
    public ClaimResponse decline(@PathVariable String reference,
                                 @RequestBody(required = false) MessageRequest request) {
        String reason = request == null ? null : request.getReason();
        claims.decline(reference, reason);
        return ClaimResponse.from(claims.getDetail(reference));
    }

    @PostMapping("/claims/{reference}/withdraw")
    public ClaimResponse withdraw(@PathVariable String reference) {
        claims.withdraw(reference);
        return ClaimResponse.from(claims.getDetail(reference));
    }

    /** Counts for the dashboard header. */
    @GetMapping("/claims/summary")
    public Map<String, Long> summary(@RequestParam String email) {
        List<Claim> involved = claims.involving(email);
        return Map.of(
                "total", (long) involved.size(),
                "open", involved.stream().filter(c -> c.getStatus().name().equals("OPEN")).count(),
                "accepted", involved.stream().filter(c -> c.getStatus().name().equals("ACCEPTED")).count());
    }
}
