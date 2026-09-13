package com.teamproteinpowder.lostfound.web.dto;

import java.time.Instant;

import com.teamproteinpowder.lostfound.domain.Comment;

/** A comment on an item. If privateMessage is true, it is only returned to authorized users. */
public record CommentResponse(Long id, String authorName, String body, boolean privateMessage, Instant createdAt) {

    public static CommentResponse from(Comment c) {
        return new CommentResponse(c.getId(), c.getAuthorName(), c.getBody(), c.isPrivateMessage(), c.getCreatedAt());
    }
}
