package com.teamproteinpowder.lostfound.web.dto;

import jakarta.validation.constraints.*;

/** Replace a report's public incident location; null coordinates remove the pin. */
public record LocationRequest(
        @NotBlank @Size(max = 160) String location,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Min(10) @Max(500) Integer searchRadiusMeters) {
    @AssertTrue(message = "Set both coordinates, or leave both empty")
    public boolean isCoordinatesPaired() {
        return (latitude == null && longitude == null)
                || (latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude));
    }
}
