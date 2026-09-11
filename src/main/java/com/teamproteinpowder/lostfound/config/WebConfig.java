package com.teamproteinpowder.lostfound.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.teamproteinpowder.lostfound.service.StorageService;

/**
 * Serves uploaded photographs from the on-disk upload directory at /uploads/**.
 *
 * They live outside the classpath deliberately: bundling user uploads into
 * src/main/resources would mean a rebuild to see a new photo, and would ship
 * them inside the jar.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StorageService storage;

    public WebConfig(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(storage.getRoot().toUri().toString())
                .setCachePeriod(3600);
    }
}
