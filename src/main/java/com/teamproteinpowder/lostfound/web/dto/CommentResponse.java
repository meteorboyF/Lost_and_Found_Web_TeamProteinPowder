package com.teamproteinpowder.lostfound.web.dto;

import java.time.Instant;

import com.teamproteinpowder.lostfound.domain.Comment;
import com.teamproteinpowder.lostfound.domain.User;

/**
 * A comment on an item. If privateMessage is true, it is only returned to
 * authorized users.
 *
 * {@code verified} is true only when the author is an account the app
 * currently trusts. A guest chooses their own display name, so without this
 * flag a reader could not tell a real "admin" from a guest who typed "admin" —
 * an easy way to impersonate staff and scam the person who lost something.
 *
 * Being bound to an account is not enough: the account must be approved,
 * the same rule login applies. Otherwise someone an admin
 * later rejects — a caught scammer, say — would keep a Verified badge on
 * everything they had already posted.
 *
 * Callers must invoke {@link #from} inside a transaction: the author is
 * lazily loaded, and open-in-view is off.
 */
public record CommentResponse(Long id, String authorName, String body, boolean privateMessage,
                              boolean verified, Instant createdAt) {

    public static CommentResponse from(Comment c) {
        return new CommentResponse(c.getId(), c.getAuthorName(), c.getBody(), c.isPrivateMessage(),
                isTrusted(c.getUser()), c.getCreatedAt());
    }

    private static boolean isTrusted(User author) {
        return author != null
                && author.isApproved();
    }
}
