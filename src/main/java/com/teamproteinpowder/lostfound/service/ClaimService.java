package com.teamproteinpowder.lostfound.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.Year;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.teamproteinpowder.lostfound.domain.Claim;
import com.teamproteinpowder.lostfound.domain.ClaimMessage;
import com.teamproteinpowder.lostfound.domain.ClaimStatus;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.ItemStatus;
import com.teamproteinpowder.lostfound.repo.ClaimMessageRepository;
import com.teamproteinpowder.lostfound.repo.ClaimRepository;
import com.teamproteinpowder.lostfound.repo.ItemRepository;
import com.teamproteinpowder.lostfound.web.dto.ClaimRequest;

@Service
public class ClaimService {

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final ClaimRepository claims;
    private final ClaimMessageRepository messages;
    private final ItemRepository items;
    private final SecureRandom random = new SecureRandom();

    public ClaimService(ClaimRepository claims, ClaimMessageRepository messages, ItemRepository items) {
        this.claims = claims;
        this.messages = messages;
        this.items = items;
    }

    /* ---------------------------------------------------------------------
       Creating a claim
       --------------------------------------------------------------------- */

    @Transactional
    public Claim create(Item item, ClaimRequest request) {
        if (item.getStatus() == ItemStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This item has already been returned to its owner");
        }

        String email = request.getClaimantEmail().trim().toLowerCase();

        if (email.equalsIgnoreCase(item.getReporterEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This is your own post — you cannot claim it");
        }

        if (claims.existsByItemAndClaimantEmailIgnoreCaseAndStatus(item, email, ClaimStatus.OPEN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have an open claim on this item");
        }

        Claim claim = new Claim();
        claim.setReference(nextReference());
        claim.setItem(item);
        claim.setStatus(ClaimStatus.OPEN);
        claim.setClaimantName(request.getClaimantName().trim());
        claim.setClaimantEmail(email);
        claim.setProof(request.getProof().trim());
        claims.save(claim);

        /* The proof becomes the opening message, so the thread reads as one
           continuous conversation rather than a form followed by a chat. */
        addMessage(claim, ClaimMessage.Author.CLAIMANT, claim.getClaimantName(), claim.getProof());

        /* An item with someone asking about it is no longer simply "open". */
        if (item.getStatus() == ItemStatus.OPEN) {
            item.setStatus(ItemStatus.PENDING);
            items.save(item);
        }

        return claim;
    }

    /* ---------------------------------------------------------------------
       Conversation
       --------------------------------------------------------------------- */

    @Transactional
    public ClaimMessage reply(String reference, String body, boolean fromPoster) {
        Claim claim = require(reference);
        if (claim.getStatus() != ClaimStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This conversation is closed");
        }
        String name = fromPoster ? claim.getItem().getReporterName() : claim.getClaimantName();
        ClaimMessage message = addMessage(claim,
                fromPoster ? ClaimMessage.Author.POSTER : ClaimMessage.Author.CLAIMANT,
                name, body.trim());
        claim.setUpdatedAt(Instant.now());
        claims.save(claim);
        return message;
    }

    private ClaimMessage addMessage(Claim claim, ClaimMessage.Author author, String name, String body) {
        ClaimMessage message = new ClaimMessage();
        message.setClaim(claim);
        message.setAuthor(author);
        message.setAuthorName(name);
        message.setBody(body);
        messages.save(message);
        claim.getMessages().add(message);
        return message;
    }

    /* ---------------------------------------------------------------------
       Resolution
       --------------------------------------------------------------------- */

    @Transactional
    public Claim accept(String reference) {
        Claim claim = require(reference);
        requireOpen(claim);

        claim.setStatus(ClaimStatus.ACCEPTED);
        claim.setResolvedAt(Instant.now());
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry",
                claim.getItem().getReporterName() + " confirmed this is yours. Arrange the handover between you.");
        claims.save(claim);

        Item item = claim.getItem();
        item.setStatus(ItemStatus.RESOLVED);
        items.save(item);

        /* Everyone else asking about this object is now out of luck; close
           their threads explicitly rather than leaving them waiting. */
        claims.findForItem(item).stream()
                .filter(other -> !other.getId().equals(claim.getId()))
                .filter(other -> other.getStatus() == ClaimStatus.OPEN)
                .forEach(other -> {
                    other.setStatus(ClaimStatus.DECLINED);
                    other.setResolvedAt(Instant.now());
                    addMessage(other, ClaimMessage.Author.SYSTEM, "Registry",
                            "This item has been returned to someone else.");
                    claims.save(other);
                });

        return claim;
    }

    @Transactional
    public Claim decline(String reference, String reason) {
        Claim claim = require(reference);
        requireOpen(claim);

        claim.setStatus(ClaimStatus.DECLINED);
        claim.setResolvedAt(Instant.now());
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry",
                (reason == null || reason.isBlank())
                        ? "The person who posted this does not think it is a match."
                        : "Declined: " + reason.trim());
        claims.save(claim);

        releaseItemIfQuiet(claim);
        return claim;
    }

    @Transactional
    public Claim withdraw(String reference) {
        Claim claim = require(reference);
        requireOpen(claim);

        claim.setStatus(ClaimStatus.WITHDRAWN);
        claim.setResolvedAt(Instant.now());
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry",
                claim.getClaimantName() + " withdrew this claim.");
        claims.save(claim);

        releaseItemIfQuiet(claim);
        return claim;
    }

    /**
     * Put the item back on the board once nothing is outstanding. Without this
     * a single declined claim would leave the item stuck at PENDING forever.
     */
    private void releaseItemIfQuiet(Claim claim) {
        Item item = claim.getItem();
        if (item.getStatus() == ItemStatus.RESOLVED) {
            return;
        }
        if (claims.countByItemAndStatus(item, ClaimStatus.OPEN) == 0) {
            item.setStatus(ItemStatus.OPEN);
            items.save(item);
        }
    }

    /** Load inside the caller's transaction so lazy associations stay usable. */
    private Claim require(String reference) {
        return claims.findByReference(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No claim with reference " + reference));
    }

    private static void requireOpen(Claim claim) {
        if (claim.getStatus() != ClaimStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This claim is already " + claim.getStatus().name().toLowerCase());
        }
    }

    /* ---------------------------------------------------------------------
       Lookups
       --------------------------------------------------------------------- */

    /** For reading: item and messages are fetch-joined so the DTO can be built
        after the transaction closes. */
    @Transactional(readOnly = true)
    public Claim getDetail(String reference) {
        return claims.findDetailByReference(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No claim with reference " + reference));
    }

    /** For mutating: the caller is already inside a transaction. */
    @Transactional(readOnly = true)
    public Claim getByReference(String reference) {
        return claims.findByReference(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No claim with reference " + reference));
    }

    @Transactional(readOnly = true)
    public List<Claim> forItem(Item item) {
        return claims.findForItem(item);
    }

    @Transactional(readOnly = true)
    public List<Claim> byReferences(List<String> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        return claims.findByReferences(references);
    }

    @Transactional(readOnly = true)
    public List<Claim> involving(String email) {
        return claims.findInvolving(email.trim().toLowerCase());
    }

    private String nextReference() {
        String year = String.valueOf(Year.now().getValue());
        for (int attempt = 0; attempt < 12; attempt++) {
            StringBuilder suffix = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                suffix.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            }
            String candidate = "CL-" + year + "-" + suffix;
            if (!claims.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique claim reference");
    }
}
