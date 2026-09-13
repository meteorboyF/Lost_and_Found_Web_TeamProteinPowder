package com.teamproteinpowder.lostfound.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * One post on the board — either something a person lost or something they
 * found and handed in.
 */
@Entity
@Table(name = "items", indexes = {
        @Index(name = "idx_item_kind", columnList = "kind"),
        @Index(name = "idx_item_status", columnList = "status"),
        @Index(name = "idx_item_category", columnList = "category"),
        @Index(name = "idx_item_created", columnList = "createdAt")
})
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Short human-quotable code shown in the UI and usable in conversation
     * ("I'm asking about LF-24081"). Generated on insert; the numeric id stays
     * an internal detail.
     */
    @Column(nullable = false, unique = true, length = 16)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ItemKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemStatus status = ItemStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 40)
    private String colour;

    /** Where it was lost or found, as a person would say it. */
    @Column(nullable = false, length = 160)
    private String location;

    /** Optional map pin, filled in by the campus map picker. */
    private Double latitude;
    private Double longitude;

    /** The day it went missing or was picked up — often not the day it was posted. */
    private LocalDate happenedOn;

    /** Path under /uploads, or a bundled placeholder for seeded rows. */
    @Column(length = 300)
    private String photoUrl;

    @Column(nullable = false, length = 80)
    private String reporterName;

    @Column(nullable = false, length = 160)
    private String reporterEmail;

    /**
     * The account that created this, when one was signed in.
     *
     * Nullable on purpose: posts predating accounts, and guest submissions,
     * still have to be representable. The reporter_email column stays as the
     * contact of record so a guest post is not anonymous.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;


    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = ItemStatus.OPEN;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // --- accessors --------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public ItemKind getKind() {
        return kind;
    }

    public void setKind(ItemKind kind) {
        this.kind = kind;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public LocalDate getHappenedOn() {
        return happenedOn;
    }

    public void setHappenedOn(LocalDate happenedOn) {
        this.happenedOn = happenedOn;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getReporterEmail() {
        return reporterEmail;
    }

    public void setReporterEmail(String reporterEmail) {
        this.reporterEmail = reporterEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
