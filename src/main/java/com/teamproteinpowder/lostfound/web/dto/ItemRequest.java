package com.teamproteinpowder.lostfound.web.dto;

import java.time.LocalDate;

import com.teamproteinpowder.lostfound.domain.Category;
import com.teamproteinpowder.lostfound.domain.ItemKind;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * What the post form sends. Deliberately separate from the entity so a client
 * cannot set status, reference, or timestamps by including them in the body.
 */
public class ItemRequest {

    @NotNull(message = "Choose whether this item was lost or found")
    private ItemKind kind;

    @NotNull(message = "Pick a category")
    private Category category;

    @NotBlank(message = "Give the item a short title")
    @Size(max = 120, message = "Keep the title under 120 characters")
    private String title;

    @NotBlank(message = "Describe the item")
    @Size(max = 2000, message = "Keep the description under 2000 characters")
    private String description;

    @Size(max = 40, message = "Keep the colour under 40 characters")
    private String colour;

    @NotBlank(message = "Say roughly where it happened")
    @Size(max = 160, message = "Keep the location under 160 characters")
    private String location;

    private Double latitude;
    private Double longitude;

    @PastOrPresent(message = "The date cannot be in the future")
    private LocalDate happenedOn;

    @NotBlank(message = "Tell us your name")
    @Size(max = 80, message = "Keep the name under 80 characters")
    private String reporterName;

    @NotBlank(message = "We need an email address to reach you")
    @Email(message = "That does not look like a valid email address")
    @Size(max = 160)
    private String reporterEmail;

    /** Set by the controller after the upload is written; never sent by the client. */
    private String photoUrl;

    public ItemKind getKind() {
        return kind;
    }

    public void setKind(ItemKind kind) {
        this.kind = kind;
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

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}
