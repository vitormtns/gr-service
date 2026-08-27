package com.gerenciadorrural.shared.security.infrastructure;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@AutoConfigureMockMvc
class SecurityHttpIntegrationTest {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";
    private static final String OTHER_SECRET = "different-test-key-with-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void meWithoutTokenReturnsJson401WithRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("X-Request-ID", "request-without-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("authentication_required"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.requestId").value("request-without-token"))
                .andExpect(header().string("X-Request-ID", "request-without-token"));
    }

    @Test
    void validTokenReturnsOnlyAuthenticatedIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();
        String token = sign(validClaims(userId)
                .claim("email", "pessoa@example.test")
                .claim("session_id", sessionId)
                .claim("aal", "aal1"), SECRET);

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("pessoa@example.test"))
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.authenticationLevel").value("aal1"))
                .andExpect(jsonPath("$.issuedAt").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.farmId").doesNotExist());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = baseClaims(UUID.randomUUID())
                .issueTime(Date.from(now.minusSeconds(120)))
                .expirationTime(Date.from(now.minusSeconds(30)));

        assertUnauthorized(sign(claims, SECRET));
    }

    @Test
    void futureNotBeforeReturns401() throws Exception {
        JWTClaimsSet.Builder claims = validClaims(UUID.randomUUID())
                .notBeforeTime(Date.from(Instant.now().plusSeconds(60)));

        assertUnauthorized(sign(claims, SECRET));
    }

    @Test
    void missingIssuedAtReturns401() throws Exception {
        JWTClaimsSet.Builder claims = baseClaims(UUID.randomUUID())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));

        assertUnauthorized(sign(claims, SECRET));
    }

    @Test
    void missingExpirationReturns401() throws Exception {
        JWTClaimsSet.Builder claims = baseClaims(UUID.randomUUID())
                .issueTime(Date.from(Instant.now().minusSeconds(5)));

        assertUnauthorized(sign(claims, SECRET));
    }

    @Test
    void incorrectIssuerReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(UUID.randomUUID()).issuer("https://wrong.example.test"), SECRET));
    }

    @Test
    void incorrectAudienceReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(UUID.randomUUID()).audience(List.of("other")), SECRET));
    }

    @Test
    void missingSubjectReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(null), SECRET));
    }

    @Test
    void nonUuidSubjectReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(null).subject("invalid-subject"), SECRET));
    }

    @Test
    void incorrectSignatureReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(UUID.randomUUID()), OTHER_SECRET));
    }

    @Test
    void nonexistentApiEndpointIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/not-found"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void actuatorInfoIsUnavailableAnd403IncludesRequestIdForAuthenticatedUser() throws Exception {
        String token = sign(validClaims(UUID.randomUUID()), SECRET);

        mockMvc.perform(get("/actuator/info")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Request-ID", "forbidden-request"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.requestId").value("forbidden-request"));
    }

    private void assertUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("authentication_required"))
                .andExpect(jsonPath("$.message").value("Autenticação necessária ou token inválido"));
    }

    private JWTClaimsSet.Builder validClaims(UUID subject) {
        Instant now = Instant.now();
        return baseClaims(subject)
                .issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private JWTClaimsSet.Builder baseClaims(UUID subject) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of("authenticated"))
                .claim("role", "authenticated");
        if (subject != null) {
            builder.subject(subject.toString());
        }
        return builder;
    }

    private String sign(JWTClaimsSet.Builder claims, String secret) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                claims.build());
        JWSSigner signer = new MACSigner(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jwt.sign(signer);
        return jwt.serialize();
    }
}
