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
import com.teamproteinpowder.lostfound.domain.User;
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
    private final AccessService access;
    private final QuestionService questions;
    private final com.teamproteinpowder.lostfound.repo.ClaimAuditRepository audit;
    private final jakarta.persistence.EntityManager entityManager;
    private final SecureRandom random = new SecureRandom();

    public ClaimService(ClaimRepository claims, ClaimMessageRepository messages, ItemRepository items,
                        AccessService access, QuestionService questions, jakarta.persistence.EntityManager entityManager,
                        com.teamproteinpowder.lostfound.repo.ClaimAuditRepository audit) {
        this.claims = claims;
        this.messages = messages;
        this.items = items;
        this.access = access;
        this.questions = questions;
        this.entityManager = entityManager;
        this.audit = audit;
    }

    /* ---------------------------------------------------------------------
       Creating a claim
       --------------------------------------------------------------------- */

    @Transactional
    public Claim create(Item item, ClaimRequest request, User user) {
        item = items.lockByReference(item.getReference()).orElseThrow();
        entityManager.refresh(item);
        if (item.getStatus() == ItemStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This item has already been returned to its owner");
        }

        String email = user.getEmail().trim().toLowerCase(java.util.Locale.ROOT);

        if (access.owns(item, user) || email.equalsIgnoreCase(item.getReporterEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This is your own post — you cannot claim it");
        }

        if (claims.existsByItemAndClaimantEmailIgnoreCaseAndStatus(item, email, ClaimStatus.OPEN)
                || claims.existsByItemAndClaimantEmailIgnoreCaseAndStatus(item, email, ClaimStatus.APPROVED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have an open claim on this item");
        }

        Claim claim = new Claim();
        questions.verify(item, user, request.getSecurityAnswer());
        if (request.getProof().trim().length() < 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give at least 20 characters of proof");
        }
        claim.setReference(nextReference());
        claim.setItem(item);
        claim.setStatus(ClaimStatus.OPEN);
        claim.setClaimantName(user.getUsername());
        claim.setClaimantEmail(email);
        claim.setProof(request.getProof().trim());
        claim.setUser(user);
        claim.setChatUnlocked(item.getSecurityAnswerHash() != null);
        claim.setDeskReviewStatus(item.isDeskReviewRequired() ? "PENDING" : "NONE");
        claims.save(claim);
        record(claim, user, "CLAIM_CREATED", "Private ownership description submitted.");

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
    public ClaimMessage reply(String reference, String body, User user) {
        Claim claim = require(reference);
        boolean fromPoster = "POSTER".equals(access.requireParticipant(claim, user));
        requireVerified(claim);
        if (claim.getStatus().isClosed()) {
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

    @Transactional
    public void verifyProof(String reference, User user) {
        Claim claim = require(reference);
        access.requireOwner(claim.getItem(), user);
        requireOpen(claim);
        claim.setEvidenceReviewedAt(Instant.now());
        record(claim, user, "EVIDENCE_REVIEWED", "Poster reviewed the private ownership evidence.");
        if (!claim.isChatUnlocked()) {
            claim.setChatUnlocked(true);
            addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry", "The poster reviewed the ownership proof and unlocked chat.");
            claims.save(claim);
        }
        claims.save(claim);
    }

    private static void requireVerified(Claim claim) {
        if (!claim.isChatUnlocked()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Chat is locked until the poster verifies the ownership proof");
    }

    /* ---------------------------------------------------------------------
       Resolution
       --------------------------------------------------------------------- */

    @Transactional
    public Claim accept(String reference, User user) {
        Claim claim = require(reference);
        access.requireOwner(claim.getItem(), user);
        requireVerified(claim);
        requireOpen(claim);
        requireSafe(claim);
        if (claim.getEvidenceReviewedAt() == null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Review the private ownership evidence before approving this claim");
        if (claims.findForItem(claim.getItem()).stream().anyMatch(c -> c.getStatus() == ClaimStatus.APPROVED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another claim is already approved for pickup");
        }
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setApprovedAt(Instant.now());
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry",
                "Claim approved, awaiting pickup. Both participants must confirm the actual handover before this item is marked returned.");
        claims.save(claim);
        record(claim, user, "CLAIM_APPROVED", "Awaiting two-party handover confirmation.");
        return claim;
    }

    @Transactional
    public Claim confirmHandover(String reference, User user) {
        Claim claim = require(reference);
        String role = access.requireParticipant(claim, user);
        if (claim.getStatus() != ClaimStatus.APPROVED) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only an approved claim can confirm handover");
        requireSafe(claim);
        boolean poster = "POSTER".equals(role);
        if ((poster ? claim.getPosterHandoverAt() : claim.getClaimantHandoverAt()) != null) return claim;
        if (poster) claim.setPosterHandoverAt(Instant.now()); else claim.setClaimantHandoverAt(Instant.now());
        record(claim, user, "HANDOVER_CONFIRMED", role + " confirmed the actual handover.");
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry", user.getUsername() + " confirmed handover.");
        if (claim.getPosterHandoverAt() == null || claim.getClaimantHandoverAt() == null) return claims.save(claim);
        claim.setStatus(ClaimStatus.ACCEPTED);
        claim.setResolvedAt(Instant.now());
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry", "Both participants confirmed handover. Item returned.");
        record(claim, user, "HANDOVER_COMPLETED", "Both participants confirmed; item marked returned.");
        claims.save(claim);

        Item item = claim.getItem();
        item.setStatus(ItemStatus.RESOLVED);
        items.save(item);

        /* Everyone else asking about this object is now out of luck; close
           their threads explicitly rather than leaving them waiting. */
        claims.findForItem(item).stream()
                .filter(other -> !other.getId().equals(claim.getId()))
                .filter(other -> !other.getStatus().isClosed())
                .forEach(other -> {
                    other.setStatus(ClaimStatus.DECLINED);
                    other.setResolvedAt(Instant.now());
                    addMessage(other, ClaimMessage.Author.SYSTEM, "Registry",
                            "This item has been returned to someone else.");
                    claims.save(other);
                    record(other, user, "OTHER_CLAIM_CLOSED", "Another claim completed the two-party handover.");
                });

        return claim;
    }

    @Transactional
    public Claim decline(String reference, String reason, User user) {
        Claim claim = require(reference);
        access.requireOwner(claim.getItem(), user);
        requireActive(claim);

        claim.setStatus(ClaimStatus.DECLINED);
        claim.setResolvedAt(Instant.now());
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry",
                (reason == null || reason.isBlank())
                        ? "The person who posted this does not think it is a match."
                        : "Declined: " + reason.trim());
        claims.save(claim);
        record(claim, user, "CLAIM_DECLINED", reason == null || reason.isBlank() ? "Poster declined claim." : reason.trim());

        releaseItemIfQuiet(claim);
        return claim;
    }

    @Transactional
    public Claim withdraw(String reference, User user) {
        Claim claim = require(reference);
        if (!"CLAIMANT".equals(access.requireParticipant(claim, user))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the claimant can withdraw a claim");
        }
        requireActive(claim);

        claim.setStatus(ClaimStatus.WITHDRAWN);
        claim.setResolvedAt(Instant.now());
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry",
                claim.getClaimantName() + " withdrew this claim.");
        claims.save(claim);
        record(claim, user, "CLAIM_WITHDRAWN", "Claimant withdrew the claim.");

        releaseItemIfQuiet(claim);
        return claim;
    }

    /**
     * The poster closes their own report: the owner got it back outside the
     * app, or the finder handed it to campus security. Before this, the only
     * way out of OPEN was a completed claim, so a self-recovered item stayed
     * on the board forever and kept drawing claims and answer guesses.
     */
    @Transactional
    public Item closeByOwner(String itemReference, User user) {
        Item item = items.lockByReference(itemReference).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No item with reference " + itemReference));
        entityManager.refresh(item);
        access.requireOwner(item, user);
        if (item.getStatus() == ItemStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This report is already closed");
        }
        /* A reported dispute is for staff to settle; closing would silently
           decline the claims they are reviewing. */
        if (item.isHandoverFrozen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Campus staff are reviewing a dispute on this item. It can be closed once they finish");
        }
        if (claims.countByItemAndStatus(item, ClaimStatus.APPROVED) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A claim is approved for pickup. Complete that handover or decline the claim first");
        }

        item.setStatus(ItemStatus.RESOLVED);
        items.save(item);

        /* Tell everyone still waiting, rather than leaving their threads open. */
        for (Claim other : claims.findForItem(item)) {
            if (other.getStatus().isClosed()) continue;
            other.setStatus(ClaimStatus.DECLINED);
            other.setResolvedAt(Instant.now());
            addMessage(other, ClaimMessage.Author.SYSTEM, "Registry",
                    "The poster closed this report, so this claim has ended.");
            claims.save(other);
            record(other, user, "REPORT_CLOSED", "Poster closed the report.");
        }
        return item;
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
        if (claims.countByItemAndStatus(item, ClaimStatus.OPEN) + claims.countByItemAndStatus(item, ClaimStatus.APPROVED) == 0) {
            item.setStatus(ItemStatus.OPEN);
            items.save(item);
        }
    }

    /** Load inside the caller's transaction so lazy associations stay usable. */
    private Claim require(String reference) {
        Claim claim = claims.findByReference(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No claim with reference " + reference));
        items.lockByReference(claim.getItem().getReference()).orElseThrow();
        entityManager.refresh(claim.getItem());
        entityManager.refresh(claim);
        return claim;
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

    private static void requireActive(Claim claim) {
        if (claim.getStatus().isClosed()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This claim is closed");
    }
    private static void requireSafe(Claim claim) {
        if (claim.getItem().isHandoverFrozen()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Handover is frozen until campus staff resolves every dispute on this item");
        if (!java.util.Set.of("NONE", "CLEARED").contains(claim.getDeskReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Campus-desk review and an in-person student-ID check are required");
        }
        if (claim.getItem().isDeskReviewRequired() && (!"CLEARED".equals(claim.getDeskReviewStatus())
                || claim.getStudentIdCheckedAt() == null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This valuable item requires campus-desk clearance");
        }
    }
    private void record(Claim claim, User actor, String action, String note) {
        audit.save(new com.teamproteinpowder.lostfound.domain.ClaimAudit(claim, actor, action, note));
    }

    @Transactional
    public Claim attachEvidence(String reference, String photoName, User actor) {
        Claim claim = require(reference);
        if (!"CLAIMANT".equals(access.requireParticipant(claim, actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the claimant can submit evidence");
        }
        requireOpen(claim);
        if (claim.getEvidencePhotoName() != null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Evidence image already submitted; ask campus staff before replacing it");
        claim.setEvidencePhotoName(photoName);
        claim.setEvidenceReviewedAt(null);
        if ("CLEARED".equals(claim.getDeskReviewStatus())) {
            claim.setDeskReviewStatus("PENDING"); claim.setStudentIdCheckedAt(null);
        }
        record(claim, actor, "EVIDENCE_UPLOADED", "Private image evidence submitted; review is required.");
        return claims.save(claim);
    }

    @Transactional
    public Claim requestDesk(String reference, String reason, boolean dispute, User actor) {
        if (reason == null || reason.trim().length() < 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give at least 10 characters of detail");
        Claim claim = require(reference);
        access.requireParticipant(claim, actor);
        requireActive(claim);
        claim.setDeskReviewStatus("PENDING"); claim.setStudentIdCheckedAt(null);
        claim.setPosterHandoverAt(null); claim.setClaimantHandoverAt(null);
        if (dispute) {
            claim.setDisputed(true); claim.getItem().setHandoverFrozen(true);
            items.save(claim.getItem());
            // A dispute invalidates all previous handover confirmations for this item.
            for (Claim other : claims.findForItem(claim.getItem())) {
                if (!other.getStatus().isClosed()) {
                    other.setPosterHandoverAt(null); other.setClaimantHandoverAt(null); claims.save(other);
                }
            }
        }
        record(claim, actor, dispute ? "FRAUD_REPORTED" : "DESK_REVIEW_REQUESTED", reason.trim());
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Registry", dispute
                ? "Suspicious claim reported. All handovers for this item are frozen pending staff review."
                : "Campus-desk review requested. Arrange an in-person student-ID check with staff.");
        return claims.save(claim);
    }

    @Transactional
    public Claim deskReview(String reference, boolean clear, boolean idChecked, String note, User staff) {
        if (staff.getRole() != com.teamproteinpowder.lostfound.domain.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Campus staff access required");
        }
        Claim claim = require(reference);
        if (access.role(claim, staff) != null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Staff cannot review their own report or claim; another staff member must review it");
        if (note == null || note.trim().length() < 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give at least 10 characters of review notes");
        if (!"PENDING".equals(claim.getDeskReviewStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This claim is not awaiting campus-desk review");
        if (clear && !idChecked) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Confirm an in-person student-ID check before clearing this claim");
        claim.setDeskReviewStatus(clear ? "CLEARED" : "REJECTED");
        claim.setStudentIdCheckedAt(clear ? Instant.now() : null);
        claim.setDisputed(false); claim.setPosterHandoverAt(null); claim.setClaimantHandoverAt(null);
        if (!clear && !claim.getStatus().isClosed()) { claim.setStatus(ClaimStatus.DECLINED); claim.setResolvedAt(Instant.now()); }
        claims.saveAndFlush(claim);
        boolean frozen = claims.findForItem(claim.getItem()).stream().anyMatch(Claim::isDisputed);
        claim.getItem().setHandoverFrozen(frozen); items.save(claim.getItem());
        record(claim, staff, clear ? "DESK_CLEARED" : "DESK_REJECTED", note.trim());
        addMessage(claim, ClaimMessage.Author.SYSTEM, "Campus desk", clear
                ? "Staff reviewed the evidence and confirmed an in-person student-ID check. Desk review cleared."
                : "Campus staff rejected this claim after review.");
        if (!clear) releaseItemIfQuiet(claim);
        return claim;
    }

    @Transactional(readOnly = true)
    public List<Claim> forUser(User user) {
        return claims.findForUser(user.getId());
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
