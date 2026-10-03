package com.teamproteinpowder.lostfound.domain;

import java.time.Instant;
import jakarta.persistence.*;

/** Append-only through the application; scalar references survive removal of a claim or account. */
@Entity
@Table(name = "claim_audit", indexes = @Index(name = "idx_audit_claim", columnList = "claimReference"))
public class ClaimAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false, length = 16) private String claimReference;
    @Column(nullable = false, updatable = false, length = 16) private String itemReference;
    @Column(nullable = false, updatable = false, length = 40) private String action;
    @Column(updatable = false) private Long actorId;
    @Column(nullable = false, updatable = false, length = 80) private String actorName;
    @Column(nullable = false, updatable = false, length = 1000) private String note;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    protected ClaimAudit() {}
    public ClaimAudit(Claim claim, User actor, String action, String note) {
        claimReference = claim.getReference(); itemReference = claim.getItem().getReference();
        actorId = actor.getId(); actorName = actor.getUsername(); this.action = action;
        this.note = note; createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getClaimReference() { return claimReference; }
    public String getItemReference() { return itemReference; }
    public String getAction() { return action; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
