package com.teamproteinpowder.lostfound.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamproteinpowder.lostfound.domain.Comment;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.repo.CommentRepository;
import com.teamproteinpowder.lostfound.service.ItemService;
import com.teamproteinpowder.lostfound.service.CurrentUser;
import com.teamproteinpowder.lostfound.service.AccessService;
import com.teamproteinpowder.lostfound.service.RateLimiter;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.web.dto.CommentRequest;
import com.teamproteinpowder.lostfound.web.dto.CommentResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Comments on an item listing. Supports public discussion as well as
 * private messages / secret information visible only to the post owner,
 * the comment author, and administrators.
 */
@RestController
@RequestMapping("/api/items/{reference}/comments")
public class CommentController {

    private final CommentRepository comments;
    private final ItemService items;
    private final String adminKey;
    private final CurrentUser currentUser;
    private final AccessService access;

    private final RateLimiter rateLimiter;

    public CommentController(CommentRepository comments,
                             ItemService items,
                             @Value("${app.admin.key}") String adminKey, CurrentUser currentUser, AccessService access,
                             RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.comments = comments;
        this.items = items;
        this.adminKey = adminKey;
        this.currentUser = currentUser;
        this.access = access;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CommentResponse> list(@PathVariable String reference,
                                      @RequestHeader(value = "X-Admin-Key", required = false) String xAdminKey,
                                      HttpServletRequest httpRequest) {
        Item item = items.getByReference(reference);

        User user = currentUser.from(httpRequest).orElse(null);
        boolean isAdmin = (user != null && user.getRole() == Role.ADMIN)
                || (adminKey != null && !adminKey.isBlank() && adminKey.equals(xAdminKey));
        boolean isPostOwner = access.owns(item, user);

        return comments.findVisibleForItem(item).stream()
                .filter(c -> {
                    if (!c.isPrivateMessage()) {
                        return true;
                    }
                    if (isAdmin || isPostOwner) {
                        return true;
                    }
                    if (user != null && c.getUser() != null && user.getId().equals(c.getUser().getId())) {
                        return true;
                    }
                    return false;
                })
                .map(CommentResponse::from)
                .toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CommentResponse> add(@PathVariable String reference,
                                               @Valid @RequestBody CommentRequest request,
                                               HttpServletRequest httpRequest) {
        Item item = items.getByReference(reference);
        if (request.isPrivateMessage()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Open a verified claim to send private messages");
        }
        User user = currentUser.from(httpRequest).orElse(null);
        /* Members are limited per account, so a shared campus address doesn't
           throttle a whole network; guests can only be told apart by address. */
        if (user != null) {
            rateLimiter.consume(RateLimiter.MEMBER_COMMENT, "user:" + user.getId());
        } else {
            rateLimiter.consume(RateLimiter.GUEST_COMMENT, httpRequest.getRemoteAddr());
        }
        String sessionEmail = user == null ? null : user.getEmail();
        String sessionName = user == null ? null : user.getUsername();

        String authorName = request.getAuthorName();
        if (sessionName != null) {
            authorName = sessionName;
        }

        String authorEmail = request.getAuthorEmail();
        if (sessionEmail != null) {
            authorEmail = sessionEmail;
        }

        Comment comment = new Comment();
        comment.setItem(item);
        comment.setUser(user);
        comment.setAuthorName(authorName != null ? authorName.trim() : "Anonymous");
        comment.setAuthorEmail(authorEmail == null ? null : authorEmail.trim().toLowerCase());
        comment.setBody(request.getBody().trim());
        comment.setPrivateMessage(request.isPrivateMessage());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponse.from(comments.save(comment)));
    }
}
