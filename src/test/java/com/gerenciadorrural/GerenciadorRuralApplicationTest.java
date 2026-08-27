package com.gerenciadorrural;

import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.security.supabase.mode=HMAC",
        "app.security.supabase.algorithm=HS256",
        "app.security.supabase.issuer=https://auth.example.test/auth/v1",
        "app.security.supabase.hmac-secret=test-only-hmac-key-with-at-least-32-bytes",
        "app.security.supabase.audiences=authenticated",
        "app.security.supabase.accepted-token-roles=authenticated",
        "app.security.supabase.clock-skew=1s"
})
class GerenciadorRuralApplicationTest extends SpringPostgresTestSupport {

    @Test
    void contextLoads() {
    }
}
