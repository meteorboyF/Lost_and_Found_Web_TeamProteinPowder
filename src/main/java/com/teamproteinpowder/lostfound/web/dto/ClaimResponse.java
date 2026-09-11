package com.teamproteinpowder.lostfound.web.dto;

import java.time.Instant;
import java.util.List;

import com.teamproteinpowder.lostfound.domain.Claim;
import com.teamproteinpowder.lostfound.domain.ClaimMessage;

/**
 * One claim, with its conversation.
 *
 * Neither party's email address is included. The claimant's address would let
 * the poster contact them outside the registry; the poster's is already
 * withheld everywhere else.
 */
public record ClaimResponse(
        String reference,
        String status,
        String claimantName,
        String posterName,
        ItemResponse item,
        List<MessageResponse> messages,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt) {

    public static ClaimResponse from(Claim claim) {
        return new ClaimResponse(
                claim.getReference(),
                claim.getStatus().name(),
                claim.getClaimantName(),
                claim.getItem().getReporterName(),
                ItemResponse.from(claim.getItem()),
                claim.getMessages().stream().map(MessageResponse::from).toList(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                claim.getResolvedAt());
    }

    /** Summary form for lists, without dragging every message along. */
    public static ClaimResponse summary(Claim claim) {
        return new ClaimResponse(
                claim.getReference(),
                claim.getStatus().name(),
                claim.getClaimantName(),
                claim.getItem().getReporterName(),
                ItemResponse.from(claim.getItem()),
                List.of(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                claim.getResolvedAt());
    }

    public record MessageResponse(
            Long id, String author, String authorName, String body, Instant createdAt) {

        public static MessageResponse from(ClaimMessage m) {
            return new MessageResponse(
                    m.getId(), m.getAuthor().name(), m.getAuthorName(), m.getBody(), m.getCreatedAt());
        }
    }
}
