package com.ispbilling.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** Tüm tarih hesapları Türkiye saatine göre yapılır; testlerde sabit saat verilebilir. */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Istanbul"));
    }
}
