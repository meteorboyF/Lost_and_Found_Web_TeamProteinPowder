package com.teamproteinpowder.lostfound.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamproteinpowder.lostfound.domain.Comment;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.repo.CommentRepository;
import com.teamproteinpowder.lostfound.service.ItemService;
import com.teamproteinpowder.lostfound.web.dto.CommentRequest;
import com.teamproteinpowder.lostfound.web.dto.CommentResponse;

import jakarta.validation.Valid;

/**
 * The public comment thread on a listing — where several people can help
 * identify something, as distinct from the private claim conversation.
 */
@RestController
@RequestMapping("/api/items/{reference}/comments")
public class CommentController {

    private final CommentRepository comments;
    private final ItemService items;

    public CommentController(CommentRepository comments, ItemService items) {
        this.comments = comments;
        this.items = items;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CommentResponse> list(@PathVariable String reference) {
        Item item = items.getByReference(reference);
        return comments.findVisibleForItem(item).stream().map(CommentResponse::from).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CommentResponse> add(@PathVariable String reference,
                                               @Valid @RequestBody CommentRequest request) {
        Item item = items.getByReference(reference);

        Comment comment = new Comment();
        comment.setItem(item);
        comment.setAuthorName(request.getAuthorName().trim());
        comment.setAuthorEmail(request.getAuthorEmail() == null
                ? null
                : request.getAuthorEmail().trim().toLowerCase());
        comment.setBody(request.getBody().trim());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponse.from(comments.save(comment)));
    }
}
