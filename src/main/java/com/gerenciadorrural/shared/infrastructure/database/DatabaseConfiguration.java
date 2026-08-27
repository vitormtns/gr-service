package com.gerenciadorrural.shared.infrastructure.database;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseAccessProperties.class)
public class DatabaseConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
