package org.fitznet.fun.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration for GamerBell HTTP endpoints.
 * Allows the Fitz-Net website origins to call /count, /api/firmware/latest, and any
 * future REST endpoints without browser CORS blocks.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "https://fitznet.org",
                        "https://www.fitznet.org",
                        "https://fitznet.doomdns.org",
                        "https://api.fitznet.doomdns.org",
                        "https://gamerbell.fitznet.doomdns.org",
                        "http://localhost:3000"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Type")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

