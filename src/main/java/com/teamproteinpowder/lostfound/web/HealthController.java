package com.teamproteinpowder.lostfound.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A trivial endpoint the frontend pings on boot to tell the difference between
 * "the API is down" and "there is genuinely nothing to show". Every list view
 * needs that distinction to pick between its error state and its empty state.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "up",
                "service", "lostfound",
                "time", Instant.now().toString());
    }
}
