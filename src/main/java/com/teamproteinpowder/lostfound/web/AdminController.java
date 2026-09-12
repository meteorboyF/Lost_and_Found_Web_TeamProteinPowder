package com.teamproteinpowder.lostfound.web;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.teamproteinpowder.lostfound.domain.ApprovalStatus;
import com.teamproteinpowder.lostfound.domain.Category;
import com.teamproteinpowder.lostfound.domain.Comment;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.ItemKind;
import com.teamproteinpowder.lostfound.domain.ItemStatus;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.CommentRepository;
import com.teamproteinpowder.lostfound.repo.ItemRepository;
import com.teamproteinpowder.lostfound.repo.UserRepository;
import com.teamproteinpowder.lostfound.service.ItemService;
import com.teamproteinpowder.lostfound.web.dto.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Moderation tools.
 *
 * ACCESS: guarded by either a logged-in user with role ADMIN, or the shared key
 * sent in the X-Admin-Key header (configured via app.admin.key).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ItemRepository items;
    private final CommentRepository comments;
    private final UserRepository userRepository;
    private final ItemService itemService;
    private final String adminKey;

    public AdminController(ItemRepository items,
                           CommentRepository comments,
                           UserRepository userRepository,
                           ItemService itemService,
                           @Value("${app.admin.key}") String adminKey) {
        this.items = items;
        this.comments = comments;
        this.userRepository = userRepository;
        this.itemService = itemService;
        this.adminKey = adminKey;
    }

    private void requireKeyOrAdmin(String provided, HttpServletRequest request) {
        // Check session for ADMIN role
        HttpSession session = request.getSession(false);
        if (session != null) {
            String role = (String) session.getAttribute(AuthController.SESSION_USER_ROLE);
            if ("ADMIN".equalsIgnoreCase(role)) {
                return;
            }
        }

        // Otherwise check X-Admin-Key header
        if (adminKey == null || adminKey.isBlank() || !adminKey.equals(provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin authorisation required");
        }
    }

    /** Lets the frontend check access before showing the workspace. */
    @PostMapping("/session")
    public Map<String, Object> verify(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                      HttpServletRequest request) {
        requireKeyOrAdmin(key, request);
        return Map.of("ok", true);
    }

    /* ---------------------------------------------------------------------
       Overview
       --------------------------------------------------------------------- */

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Map<String, Object> overview(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                        HttpServletRequest request) {
        requireKeyOrAdmin(key, request);

        ItemService.Stats stats = itemService.stats();

        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            byCategory.put(c.getLabel(), 0L);
        }

        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long postedThisWeek = 0;
        long stale = 0;
        Instant ninetyDaysAgo = Instant.now().minus(90, ChronoUnit.DAYS);

        for (Item item : items.findAll()) {
            byCategory.merge(item.getCategory().getLabel(), 1L, Long::sum);
            if (item.getCreatedAt().isAfter(weekAgo)) {
                postedThisWeek++;
            }
            if (item.getStatus() != ItemStatus.RESOLVED && item.getCreatedAt().isBefore(ninetyDaysAgo)) {
                stale++;
            }
        }

        long pendingStudents = userRepository.countByApprovalStatus(ApprovalStatus.PENDING);

        return Map.of(
                "stats", stats,
                "byCategory", byCategory,
                "postedThisWeek", postedThisWeek,
                "staleOverNinetyDays", stale,
                "comments", comments.count(),
                "pendingStudents", pendingStudents);
    }

    /* ---------------------------------------------------------------------
       Items
       --------------------------------------------------------------------- */

    @GetMapping("/items")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> allItems(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                              HttpServletRequest request) {
        requireKeyOrAdmin(key, request);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Item item : items.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reference", item.getReference());
            row.put("title", item.getTitle());
            row.put("kind", item.getKind().name());
            row.put("status", item.getStatus().name());
            row.put("category", item.getCategory().getLabel());
            row.put("location", item.getLocation());
            row.put("reporterName", item.getReporterName());
            row.put("reporterEmail", item.getReporterEmail());
            row.put("createdAt", item.getCreatedAt());
            rows.add(row);
        }
        rows.sort((a, b) -> ((Instant) b.get("createdAt")).compareTo((Instant) a.get("createdAt")));
        return rows;
    }

    @DeleteMapping("/items/{reference}")
    @Transactional
    public Map<String, Object> removeItem(@PathVariable String reference,
                                          @RequestHeader(value = "X-Admin-Key", required = false) String key,
                                          HttpServletRequest request) {
        requireKeyOrAdmin(key, request);
        Item item = items.findByReference(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No item " + reference));
        items.delete(item);
        return Map.of("removed", reference);
    }

    /* ---------------------------------------------------------------------
       Comments
       --------------------------------------------------------------------- */

    @GetMapping("/comments")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> allComments(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                                 HttpServletRequest request) {
        requireKeyOrAdmin(key, request);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Comment c : comments.findAllForModeration()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("authorName", c.getAuthorName());
            row.put("authorEmail", c.getAuthorEmail());
            row.put("body", c.getBody());
            row.put("hidden", c.isHidden());
            row.put("privateMessage", c.isPrivateMessage());
            row.put("itemReference", c.getItem().getReference());
            row.put("itemTitle", c.getItem().getTitle());
            row.put("createdAt", c.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    /** Hide or restore a comment. Hiding keeps the row, so a thread stays coherent. */
    @PostMapping("/comments/{id}/toggle")
    @Transactional
    public Map<String, Object> toggleComment(@PathVariable Long id,
                                             @RequestHeader(value = "X-Admin-Key", required = false) String key,
                                             HttpServletRequest request) {
        requireKeyOrAdmin(key, request);
        Comment comment = comments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No comment " + id));
        comment.setHidden(!comment.isHidden());
        comments.save(comment);
        return Map.of("id", id, "hidden", comment.isHidden());
    }

    /* ---------------------------------------------------------------------
       Student Verifications & Approvals
       --------------------------------------------------------------------- */

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                        HttpServletRequest request) {
        requireKeyOrAdmin(key, request);
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(UserResponse::from)
                .toList();
    }

    @PostMapping("/users/{id}/approve")
    @Transactional
    public UserResponse approveUser(@PathVariable Long id,
                                    @RequestHeader(value = "X-Admin-Key", required = false) String key,
                                    HttpServletRequest request) {
        requireKeyOrAdmin(key, request);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        return UserResponse.from(userRepository.save(user));
    }

    @PostMapping("/users/{id}/reject")
    @Transactional
    public UserResponse rejectUser(@PathVariable Long id,
                                   @RequestHeader(value = "X-Admin-Key", required = false) String key,
                                   HttpServletRequest request) {
        requireKeyOrAdmin(key, request);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setApprovalStatus(ApprovalStatus.REJECTED);
        return UserResponse.from(userRepository.save(user));
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public Map<String, Object> deleteUser(@PathVariable Long id,
                                          @RequestHeader(value = "X-Admin-Key", required = false) String key,
                                          HttpServletRequest request) {
        requireKeyOrAdmin(key, request);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        userRepository.delete(user);
        return Map.of("deleted", id);
    }
}
