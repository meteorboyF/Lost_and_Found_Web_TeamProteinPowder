package com.teamproteinpowder.lostfound.web;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.teamproteinpowder.lostfound.domain.*;
import com.teamproteinpowder.lostfound.service.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class PhotoController {
    private final StorageService storage;
    private final ItemService items;
    private final ClaimService claims;
    private final CurrentUser currentUser;
    private final AccessService access;

    public PhotoController(StorageService storage, ItemService items, ClaimService claims,
                           CurrentUser currentUser, AccessService access) {
        this.storage = storage;
        this.items = items;
        this.claims = claims;
        this.currentUser = currentUser;
        this.access = access;
    }

    @GetMapping("/uploads/{name}")
    public ResponseEntity<Resource> publicPhoto(@PathVariable String name) {
        return image(storage.resource(name, false), false);
    }

    @GetMapping("/api/items/{reference}/private-photo")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> privatePhoto(@PathVariable String reference, HttpServletRequest http) {
        User user = currentUser.require(http);
        Item item = items.getByReference(reference);
        boolean permitted = access.owns(item, user) || user.getRole() == Role.ADMIN;
        if (!permitted) permitted = claims.forUser(user).stream().anyMatch(c ->
                c.getItem().getId().equals(item.getId()) && c.getStatus() == ClaimStatus.ACCEPTED
                && "CLAIMANT".equals(access.role(c, user)));
        if (!permitted) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This photograph is private until handover is completed");
        return image(storage.resource(item.getPrivatePhotoName(), true), true);
    }

    private ResponseEntity<Resource> image(Resource resource, boolean privatePhoto) {
        String name = resource.getFilename();
        MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG
                : name.endsWith(".gif") ? MediaType.IMAGE_GIF
                : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", privatePhoto ? "no-store" : "public, max-age=3600")
                .body(resource);
    }

    @GetMapping("/api/claims/{reference}/evidence")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> evidence(@PathVariable String reference, HttpServletRequest http) {
        User user = currentUser.require(http);
        Claim claim = claims.getDetail(reference);
        if (user.getRole() != Role.ADMIN) access.requireParticipant(claim, user);
        return image(storage.resource(claim.getEvidencePhotoName(), true), true);
    }
}
