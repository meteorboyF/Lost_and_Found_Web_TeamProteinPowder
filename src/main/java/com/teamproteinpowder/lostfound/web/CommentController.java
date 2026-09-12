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
import com.teamproteinpowder.lostfound.web.dto.CommentRequest;
import com.teamproteinpowder.lostfound.web.dto.CommentResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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

    public CommentController(CommentRepository comments,
                             ItemService items,
                             @Value("${app.admin.key}") String adminKey) {
        this.comments = comments;
        this.items = items;
        this.adminKey = adminKey;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CommentResponse> list(@PathVariable String reference,
                                      @RequestHeader(value = "X-Admin-Key", required = false) String xAdminKey,
                                      HttpServletRequest httpRequest) {
        Item item = items.getByReference(reference);

        HttpSession session = httpRequest.getSession(false);
        String currentUserEmail = session != null ? (String) session.getAttribute(AuthController.SESSION_USER_EMAIL) : null;
        String currentUserRole = session != null ? (String) session.getAttribute(AuthController.SESSION_USER_ROLE) : null;

        boolean isAdmin = ("ADMIN".equalsIgnoreCase(currentUserRole))
                || (adminKey != null && !adminKey.isBlank() && adminKey.equals(xAdminKey));

        boolean isPostOwner = currentUserEmail != null && currentUserEmail.equalsIgnoreCase(item.getReporterEmail());

        return comments.findVisibleForItem(item).stream()
                .filter(c -> {
                    if (!c.isPrivateMessage()) {
                        return true;
                    }
                    if (isAdmin || isPostOwner) {
                        return true;
                    }
                    if (currentUserEmail != null && c.getAuthorEmail() != null
                            && currentUserEmail.equalsIgnoreCase(c.getAuthorEmail())) {
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

        HttpSession session = httpRequest.getSession(false);
        String sessionEmail = session != null ? (String) session.getAttribute(AuthController.SESSION_USER_EMAIL) : null;
        String sessionName = session != null ? (String) session.getAttribute(AuthController.SESSION_USER_NAME) : null;

        String authorName = request.getAuthorName();
        if ((authorName == null || authorName.isBlank()) && sessionName != null) {
            authorName = sessionName;
        }

        String authorEmail = request.getAuthorEmail();
        if ((authorEmail == null || authorEmail.isBlank()) && sessionEmail != null) {
            authorEmail = sessionEmail;
        }

        Comment comment = new Comment();
        comment.setItem(item);
        comment.setAuthorName(authorName != null ? authorName.trim() : "Anonymous");
        comment.setAuthorEmail(authorEmail == null ? null : authorEmail.trim().toLowerCase());
        comment.setBody(request.getBody().trim());
        comment.setPrivateMessage(request.isPrivateMessage());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponse.from(comments.save(comment)));
    }
}
