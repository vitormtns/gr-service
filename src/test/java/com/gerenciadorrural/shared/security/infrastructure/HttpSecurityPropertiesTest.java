package com.gerenciadorrural.shared.security.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpSecurityPropertiesTest {

    @Test
    void acceptsExplicitBrowserOrigins() {
        var cors = new HttpSecurityProperties.Cors(
                List.of("http://localhost:4200", "https://portal.example.test"),
                true,
                Duration.ofMinutes(30));

        assertThat(cors.isAllowedOriginsValid()).isTrue();
        assertThat(cors.isCredentialPolicyValid()).isTrue();
        assertThat(cors.isMaxAgeValid()).isTrue();
    }

    @Test
    void rejectsWildcardPathsAndExcessivePreflightCache() {
        assertThat(new HttpSecurityProperties.Cors(
                List.of("*"), false, Duration.ofMinutes(30)).isAllowedOriginsValid()).isFalse();
        assertThat(new HttpSecurityProperties.Cors(
                List.of("https://portal.example.test/path"), false, Duration.ofMinutes(30))
                .isAllowedOriginsValid()).isFalse();
        assertThat(new HttpSecurityProperties.Cors(
                List.of("https://portal.example.test"), false, Duration.ofHours(25))
                .isMaxAgeValid()).isFalse();
    }
}
