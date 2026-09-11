package com.teamproteinpowder.lostfound.web.dto;

import java.time.Instant;

import com.teamproteinpowder.lostfound.domain.Comment;

/** A public comment. The author's email is never included. */
public record CommentResponse(Long id, String authorName, String body, Instant createdAt) {

    public static CommentResponse from(Comment c) {
        return new CommentResponse(c.getId(), c.getAuthorName(), c.getBody(), c.getCreatedAt());
    }
}
