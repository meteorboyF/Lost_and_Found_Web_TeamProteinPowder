package com.teamproteinpowder.lostfound.web;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.teamproteinpowder.lostfound.domain.*;
import com.teamproteinpowder.lostfound.service.*;
import com.teamproteinpowder.lostfound.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
@Transactional(readOnly = true)
public class ClaimController {
    private final ClaimService claims;
    private final ItemService items;
    private final CurrentUser currentUser;
    private final AccessService access;
    private final StorageService storage;
    private final com.teamproteinpowder.lostfound.repo.ClaimAuditRepository audit;

    public ClaimController(ClaimService claims, ItemService items, CurrentUser currentUser, AccessService access,
            StorageService storage, com.teamproteinpowder.lostfound.repo.ClaimAuditRepository audit) {
        this.claims = claims;
        this.items = items;
        this.currentUser = currentUser;
        this.access = access;
        this.storage = storage; this.audit = audit;
    }

    @PostMapping(value = "/items/{reference}/claims", consumes = "application/json")
    @Transactional
    public ResponseEntity<ClaimResponse> create(@PathVariable String reference,
            @Valid @RequestBody ClaimRequest request, HttpServletRequest http) {
        User user = currentUser.require(http);
        Claim claim = claims.create(items.getByReference(reference), request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClaimResponse.from(claim, "CLAIMANT"));
    }

    @PostMapping(value = "/items/{reference}/claims", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<ClaimResponse> createWithEvidence(@PathVariable String reference,
            @Valid @RequestPart("claim") ClaimRequest request,
            @RequestPart(value = "evidence", required = false) org.springframework.web.multipart.MultipartFile evidence,
            HttpServletRequest http) {
        User user = currentUser.require(http);
        String name = null;
        try {
            name = storage.storePrivate(evidence);
            Claim claim = claims.create(items.getByReference(reference), request, user);
            if (name != null) claim = claims.attachEvidence(claim.getReference(), name, user);
            return ResponseEntity.status(HttpStatus.CREATED).body(ClaimResponse.from(claim, "CLAIMANT"));
        } catch (RuntimeException ex) { storage.deleteStored(null, name); throw ex; }
    }

    @PostMapping(value = "/claims/{reference}/evidence", consumes = "multipart/form-data")
    @Transactional
    public ClaimResponse addEvidence(@PathVariable String reference,
            @RequestPart("evidence") org.springframework.web.multipart.MultipartFile evidence, HttpServletRequest http) {
        User user = currentUser.require(http);
        String name = null;
        try {
            name = storage.storePrivate(evidence);
            if (name == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an evidence image");
            Claim claim = claims.attachEvidence(reference, name, user);
            return ClaimResponse.from(claim, "CLAIMANT");
        } catch (RuntimeException ex) { storage.deleteStored(null, name); throw ex; }
    }

    public record ReviewReason(@jakarta.validation.constraints.NotBlank @Size(min = 10, max = 1000) String reason) {}
    @PostMapping("/claims/{reference}/flag") @Transactional
    public ClaimResponse flag(@PathVariable String reference, @Valid @RequestBody ReviewReason body, HttpServletRequest http) {
        User user = currentUser.require(http);
        Claim claim = claims.requestDesk(reference, body.reason(), true, user);
        return ClaimResponse.from(claim, access.role(claim, user));
    }
    @PostMapping("/claims/{reference}/desk-review") @Transactional
    public ClaimResponse requestReview(@PathVariable String reference, @Valid @RequestBody ReviewReason body, HttpServletRequest http) {
        User user = currentUser.require(http);
        Claim claim = claims.requestDesk(reference, body.reason(), false, user);
        return ClaimResponse.from(claim, access.role(claim, user));
    }
    @PostMapping("/claims/{reference}/handover") @Transactional
    public ClaimResponse handover(@PathVariable String reference, HttpServletRequest http) {
        User user = currentUser.require(http);
        Claim claim = claims.confirmHandover(reference, user);
        return ClaimResponse.from(claim, access.role(claim, user));
    }
    @GetMapping("/claims/{reference}/audit")
    public List<com.teamproteinpowder.lostfound.domain.ClaimAudit> history(@PathVariable String reference, HttpServletRequest http) {
        access.requireParticipant(claims.getDetail(reference), currentUser.require(http));
        return audit.findByClaimReferenceOrderByIdAsc(reference);
    }

    @GetMapping("/items/{reference}/claims")
    public List<ClaimResponse> forItem(@PathVariable String reference, HttpServletRequest http) {
        User user = currentUser.require(http);
        Item item = items.getByReference(reference);
        access.requireOwner(item, user);
        return claims.forItem(item).stream().map(c -> ClaimResponse.summary(c, "POSTER")).toList();
    }

    @GetMapping("/claims/{reference}")
    public ClaimResponse one(@PathVariable String reference, HttpServletRequest http) {
        User user = currentUser.require(http);
        Claim claim = claims.getDetail(reference);
        return ClaimResponse.from(claim, access.requireParticipant(claim, user));
    }

    @GetMapping("/claims")
    public List<ClaimResponse> mine(@RequestParam(required = false) String refs,
            @RequestParam(required = false) String email, HttpServletRequest http) {
        User user = currentUser.require(http);
        if (email != null && !email.equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only list your own conversations");
        }
        List<String> selected = refs == null ? null : Arrays.asList(refs.split(","));
        return claims.forUser(user).stream()
                .filter(c -> selected == null || selected.contains(c.getReference()))
                .map(c -> ClaimResponse.summary(c, access.role(c, user))).toList();
    }

    @PostMapping("/claims/{reference}/messages")
    @Transactional
    public ClaimResponse.MessageResponse reply(@PathVariable String reference,
            @Valid @RequestBody MessageRequest request, HttpServletRequest http) {
        return ClaimResponse.MessageResponse.from(claims.reply(reference, request.getBody(), currentUser.require(http)));
    }

    @PostMapping("/claims/{reference}/verify")
    @Transactional
    public ClaimResponse verify(@PathVariable String reference, HttpServletRequest http) {
        claims.verifyProof(reference, currentUser.require(http));
        return ClaimResponse.from(claims.getDetail(reference), "POSTER");
    }

    @PostMapping("/claims/{reference}/accept")
    @Transactional
    public ClaimResponse accept(@PathVariable String reference, HttpServletRequest http) {
        claims.accept(reference, currentUser.require(http));
        return ClaimResponse.from(claims.getDetail(reference), "POSTER");
    }

    public record DeclineRequest(@Size(max = 500) String reason) {}

    @PostMapping("/claims/{reference}/decline")
    @Transactional
    public ClaimResponse decline(@PathVariable String reference,
            @Valid @RequestBody(required = false) DeclineRequest request, HttpServletRequest http) {
        claims.decline(reference, request == null ? null : request.reason(), currentUser.require(http));
        return ClaimResponse.from(claims.getDetail(reference), "POSTER");
    }

    @PostMapping("/claims/{reference}/withdraw")
    @Transactional
    public ClaimResponse withdraw(@PathVariable String reference, HttpServletRequest http) {
        claims.withdraw(reference, currentUser.require(http));
        return ClaimResponse.from(claims.getDetail(reference), "CLAIMANT");
    }

    @GetMapping("/claims/summary")
    public Map<String, Long> summary(HttpServletRequest http) {
        List<Claim> involved = claims.forUser(currentUser.require(http));
        return Map.of("total", (long) involved.size(),
                "open", involved.stream().filter(c -> !c.getStatus().isClosed()).count(),
                "accepted", involved.stream().filter(c -> c.getStatus() == ClaimStatus.ACCEPTED).count());
    }
}
