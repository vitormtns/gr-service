package com.gerenciadorrural.infrastructure.database;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class SpringPostgresTestSupport {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestEnvironment::jdbcUrl);
        registry.add("spring.datasource.username", PostgresTestEnvironment::runtimeUsername);
        registry.add("spring.datasource.password", PostgresTestEnvironment::runtimePassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 0);
        registry.add("app.security.supabase.mode", () -> "HMAC");
        registry.add("app.security.supabase.algorithm", () -> "HS256");
        registry.add("app.security.supabase.issuer", () -> "https://auth.example.test/auth/v1");
        registry.add("app.security.supabase.hmac-secret",
                () -> "test-only-hmac-key-with-at-least-32-bytes");
        registry.add("app.security.supabase.audiences", () -> "authenticated");
        registry.add("app.security.supabase.accepted-token-roles", () -> "authenticated");
        registry.add("app.security.supabase.clock-skew", () -> "1s");
    }
}
