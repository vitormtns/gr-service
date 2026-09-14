package com.gerenciadorrural;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "app.http.cors.allowed-origins=http://localhost:4200")
@AutoConfigureMockMvc
class OperationalReadinessIntegrationTest extends SpringPostgresTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void exposesPublicLivenessAndDatabaseBackedReadinessOnly() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generatesValidGroupedOpenApiWithSecurityTagsAndTenantHeaders() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs/api-v1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.path("openapi").asText()).startsWith("3.");
        assertThat(root.path("components").path("securitySchemes").has("bearer-jwt")).isTrue();
        assertThat(root.path("paths").has("/api/v1/herd/animals")).isTrue();
        JsonNode createAnimal = root.path("paths").path("/api/v1/herd/animals").path("post");
        assertThat(createAnimal.path("tags").get(0).asText()).isEqualTo("Herd");
        assertThat(createAnimal.path("security").isArray()).isTrue();
        Set<String> headerNames = new HashSet<>();
        createAnimal.path("parameters").forEach(parameter -> headerNames.add(parameter.path("name").asText()));
        assertThat(headerNames).contains("X-Organization-Id", "X-Farm-Id");
        assertThat(createAnimal.path("responses").has("400")).isTrue();
        assertThat(createAnimal.path("responses").has("409")).isTrue();
        assertThat(createAnimal.path("requestBody").path("content").path("application/json")
                .path("example").path("identification").asText()).isEqualTo("BR-2026-001");
        assertThat(root.path("paths").path("/api/v1/herd/pending-work").path("get")
                .path("tags").get(0).asText()).isEqualTo("Pending Work");
        Set<String> operationIds = new HashSet<>();
        root.path("paths").forEach(path -> path.forEach(operation -> {
            if (operation.has("operationId")) {
                assertThat(operationIds.add(operation.path("operationId").asText()))
                        .as("operationId duplicado: %s", operation.path("operationId").asText())
                        .isTrue();
            }
        }));
        assertThat(root.path("paths").size()).isGreaterThan(40);
    }

    @Test
    void allowsPreflightOnlyForConfiguredOriginAndHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/herd/animals")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,x-organization-id,x-farm-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

        mockMvc.perform(options("/api/v1/herd/animals")
                        .header("Origin", "https://host-nao-permitido.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
