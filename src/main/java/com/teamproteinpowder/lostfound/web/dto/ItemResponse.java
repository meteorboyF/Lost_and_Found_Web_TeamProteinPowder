package com.teamproteinpowder.lostfound.web.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.teamproteinpowder.lostfound.domain.Item;

/**
 * What the API returns for one item.
 *
 * The reporter's email address is NOT included. Contact happens through the
 * claim conversation, so publishing the address on a public board would be
 * handing every scraper a list of student emails.
 */
public record ItemResponse(
        Long id,
        String reference,
        String kind,
        String status,
        String category,
        String categoryLabel,
        String title,
        String description,
        String colour,
        String location,
        Double latitude,
        Double longitude,
        LocalDate happenedOn,
        String photoUrl,
        String reporterName,
        Instant createdAt) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getReference(),
                item.getKind().name(),
                item.getStatus().name(),
                item.getCategory().name(),
                item.getCategory().getLabel(),
                item.getTitle(),
                item.getDescription(),
                item.getColour(),
                item.getLocation(),
                item.getLatitude(),
                item.getLongitude(),
                item.getHappenedOn(),
                item.getPhotoUrl(),
                item.getReporterName(),
                item.getCreatedAt());
    }
}
