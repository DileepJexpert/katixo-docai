package com.katixo.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Injectable clock so "date not in the future" checks are testable/deterministic. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
