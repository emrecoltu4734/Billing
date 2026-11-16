package com.ispbilling;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Testlerde "bugün" = 1 Aralık 2026. Fatura tarihi 01.12.2026, son ödeme 16.12.2026 olur. */
@TestConfiguration
public class TestClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-12-01T06:00:00Z"), ZoneId.of("Europe/Istanbul"));
    }
}
