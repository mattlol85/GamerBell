package org.fitznet.fun.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration that provides a simple Micrometer registry.
 */
@TestConfiguration
public class TestMetricsConfiguration {

    /**
     * Creates a meter registry for MVC tests.
     *
     * @return simple meter registry
     */
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
