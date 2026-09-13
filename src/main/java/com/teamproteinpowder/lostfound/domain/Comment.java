package com.teamproteinpowder.lostfound.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A public comment on a listing.
 *
 * Distinct from a claim conversation: this is the open thread where anyone can
 * help identify something ("I saw one like that near the gym on Tuesday"),
 * whereas a claim is a private negotiation between two people.
 */
@Entity
@Table(name = "comments", indexes = {
        @Index(name = "idx_comment_item", columnList = "item_id"),
        @Index(name = "idx_comment_created", columnList = "createdAt")
})
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false, length = 80)
    private String authorName;

    /** Stored so a comment can be moderated or traced; never sent to clients. */
    @Column(length = 160)
    private String authorEmail;

    @Column(nullable = false, length = 1000)
    private String body;

    /** Hidden by moderation rather than deleted, so the thread stays coherent. */
    @Column(nullable = false)
    private boolean hidden = false;

    /** When true, only visible to post owner, comment author, and moderators. */
    @Column(nullable = false)
    private boolean privateMessage = false;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorEmail() { return authorEmail; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public boolean isPrivateMessage() { return privateMessage; }
    public void setPrivateMessage(boolean privateMessage) { this.privateMessage = privateMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
