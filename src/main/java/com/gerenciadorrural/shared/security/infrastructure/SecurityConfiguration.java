package com.gerenciadorrural.shared.security.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SupabaseSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    AuthenticatedUserMdcFilter authenticatedUserMdcFilter() {
        return new AuthenticatedUserMdcFilter();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> authenticationConverter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            AuthenticatedUserMdcFilter authenticatedUserMdcFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)))
                .addFilterAfter(authenticatedUserMdcFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(SupabaseSecurityProperties properties) {
        NimbusJwtDecoder decoder = switch (properties.mode()) {
            case JWKS -> jwksDecoder(properties);
            case HMAC -> hmacDecoder(properties);
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(properties.clockSkew()),
                new JwtIssuerValidator(properties.issuer()),
                new SupabaseJwtValidator(
                        properties.audiences(), properties.acceptedTokenRoles(), properties.clockSkew())
        ));
        return decoder;
    }

    private static NimbusJwtDecoder jwksDecoder(SupabaseSecurityProperties properties) {
        SignatureAlgorithm algorithm = switch (properties.algorithm()) {
            case ES256 -> SignatureAlgorithm.ES256;
            case RS256 -> SignatureAlgorithm.RS256;
            case HS256 -> throw new IllegalStateException("Algoritmo incompatível com JWKS");
        };
        return NimbusJwtDecoder.withJwkSetUri(properties.jwksUri()).jwsAlgorithm(algorithm).build();
    }

    private static NimbusJwtDecoder hmacDecoder(SupabaseSecurityProperties properties) {
        byte[] secret = properties.hmacSecret().getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
