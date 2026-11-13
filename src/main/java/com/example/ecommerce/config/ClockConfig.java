package com.example.ecommerce.config;

import java.time.Clock;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Makes the application clock the {@link jakarta.validation.ClockProvider} used by Bean Validation,
     * so time-based constraints such as {@code @NotExpired} follow the same clock as the rest of the app.
     */
    @Bean
    ValidationConfigurationCustomizer clockProviderCustomizer(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
