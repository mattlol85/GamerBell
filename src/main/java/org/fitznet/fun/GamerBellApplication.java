package org.fitznet.fun;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Main Spring Boot application class for GamerBell.
 * Provides extensive startup logging for Loki integration.
 */
@SpringBootApplication(scanBasePackages = "org.fitznet.fun")
@Slf4j
public class GamerBellApplication {

    private final Environment environment;

    /**
     * Constructs GamerBellApplication with environment for logging.
     *
     * @param environment the Spring environment
     */
    public GamerBellApplication(Environment environment) {
        this.environment = environment;
    }

    /**
     * Application entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        log.info("GamerBell application starting...");
        log.info("JVM info - version={}, vendor={}, runtime={}",
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.runtime.name"));

        SpringApplication.run(GamerBellApplication.class, args);
    }

    /**
     * Logs application startup details when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profiles = activeProfiles.length > 0 ? String.join(",", activeProfiles) : "default";

        log.info("========================================");
        log.info("GamerBell Application Started Successfully");
        log.info("========================================");
        log.info("Application: {}", environment.getProperty("spring.application.name", "GamerBell"));
        log.info("Active Profiles: {}", profiles);
        log.info("Server Port: {}", environment.getProperty("server.port", "8080"));
        log.info("Firmware GitHub Repo: {}", environment.getProperty("firmware.github.repo", "not configured"));
        log.info("Firmware Storage Path: {}", environment.getProperty("firmware.storage.path", "./firmware"));
        log.info("========================================");

        log.debug("Detailed Configuration:");
        log.debug("  Management Endpoints: {}", environment.getProperty("management.endpoints.web.exposure.include"));
        log.debug("  Firmware Filename: {}", environment.getProperty("firmware.filename", "firmware.bin"));

        log.info("System Resources - processors={}, maxMemoryMB={}, freeMemoryMB={}",
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024),
                Runtime.getRuntime().freeMemory() / (1024 * 1024));

        log.info("GamerBell is ready to accept WebSocket connections on /ws");
    }
}
