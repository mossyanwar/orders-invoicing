package com.mossyanwar.ordersinvoicing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Business dates (invoice dates, due dates, overdue checks) are UK dates, so the clock runs in
 * Europe/London. Code asks this bean for the time instead of calling {@code now()} directly,
 * which lets tests fix the date.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${app.timezone:Europe/London}") ZoneId zone) {
        return Clock.system(zone);
    }
}
