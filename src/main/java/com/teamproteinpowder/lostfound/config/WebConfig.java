package com.teamproteinpowder.lostfound.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


/**
 * Same-origin write protection and non-cacheable account responses.
 * PhotoController serves uploads individually so private subdirectories
 * are never exposed by a static resource handler.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response, Object handler) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Cache-Control", "no-store");
                if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
                    String origin = request.getHeader("Origin");
                    if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))
                            || (origin != null && !sameOrigin(origin, request))) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.FORBIDDEN, "Cross-origin changes are not allowed");
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/**");
    }

    private static boolean sameOrigin(String origin, jakarta.servlet.http.HttpServletRequest request) {
        try {
            java.net.URI uri = java.net.URI.create(origin);
            int port = uri.getPort() >= 0 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
            return request.getScheme().equalsIgnoreCase(uri.getScheme())
                    && request.getServerName().equalsIgnoreCase(uri.getHost())
                    && request.getServerPort() == port && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
