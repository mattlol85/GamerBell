package org.fitznet.fun;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for GamerBell.
 */
@SpringBootApplication(scanBasePackages = "org.fitznet.fun")
@Slf4j
public class GamerBellApplication {

    /**
     * Application entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(GamerBellApplication.class, args);
    }

}
