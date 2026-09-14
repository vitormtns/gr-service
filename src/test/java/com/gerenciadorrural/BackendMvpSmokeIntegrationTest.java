package com.gerenciadorrural;

import com.gerenciadorrural.infrastructure.database.PostgresTestEnvironment;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class BackendMvpSmokeIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";

    @Autowired MockMvc mvc;

    private UUID owner;
    private UUID viewer;
    private UUID outsider;
    private UUID tenant;
    private UUID otherTenant;
    private UUID farm;
    private UUID otherFarm;
    private UUID product;
    private UUID location;
    private UUID category;

    @BeforeEach
    void seedMinimumBusinessContext() throws Exception {
        PostgresTestEnvironment.clearUsers();
        owner = UUID.randomUUID();
        viewer = UUID.randomUUID();
        outsider = UUID.randomUUID();
        tenant = UUID.randomUUID();
        otherTenant = UUID.randomUUID();
        farm = UUID.randomUUID();
        otherFarm = UUID.randomUUID();
        product = UUID.randomUUID();
        location = UUID.randomUUID();
        category = UUID.randomUUID();
        UUID ownerMembership = UUID.randomUUID();
        UUID viewerMembership = UUID.randomUUID();
        UUID outsiderMembership = UUID.randomUUID();

        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("""
                     insert into app.users(id,email,status) values
                         (?, 'owner@example.test', 'ACTIVE'),
                         (?, 'viewer@example.test', 'ACTIVE'),
                         (?, 'outsider@example.test', 'ACTIVE');
                     insert into app.organizations(id,name,status) values
                         (?, 'Fazenda Horizonte', 'ACTIVE'),
                         (?, 'Organização Isolada', 'ACTIVE');
                     insert into app.farms(id,tenant_id,name,status) values
                         (?, ?, 'Unidade Norte', 'ACTIVE'),
                         (?, ?, 'Unidade Sul', 'ACTIVE');
                     insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values
                         (?, ?, ?, 'OWNER', 'ACTIVE', 'SELECTED_FARMS'),
                         (?, ?, ?, 'VIEWER', 'ACTIVE', 'SELECTED_FARMS'),
                         (?, ?, ?, 'OWNER', 'ACTIVE', 'SELECTED_FARMS');
                     insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id) values
                         (?, ?, ?), (?, ?, ?), (?, ?, ?);
                     insert into app.inventory_products(id,tenant_id,name,code,category,base_unit,status)
                         values (?, ?, 'Sal mineral', 'SAL-01', 'Suplementos', 'KG', 'ACTIVE');
                     insert into app.inventory_locations(id,tenant_id,farm_id,name,code,status)
                         values (?, ?, ?, 'Galpão principal', 'GALPAO', 'ACTIVE');
                     insert into app.financial_categories(id,tenant_id,name,code,kind,status)
                         values (?, ?, 'Venda de animais', 'VENDA', 'INCOME', 'ACTIVE');
                     """)) {
            int i = 1;
            statement.setObject(i++, owner);
            statement.setObject(i++, viewer);
            statement.setObject(i++, outsider);
            statement.setObject(i++, tenant);
            statement.setObject(i++, otherTenant);
            statement.setObject(i++, farm);
            statement.setObject(i++, tenant);
            statement.setObject(i++, otherFarm);
            statement.setObject(i++, otherTenant);
            statement.setObject(i++, ownerMembership);
            statement.setObject(i++, tenant);
            statement.setObject(i++, owner);
            statement.setObject(i++, viewerMembership);
            statement.setObject(i++, tenant);
            statement.setObject(i++, viewer);
            statement.setObject(i++, outsiderMembership);
            statement.setObject(i++, otherTenant);
            statement.setObject(i++, outsider);
            statement.setObject(i++, tenant);
            statement.setObject(i++, ownerMembership);
            statement.setObject(i++, farm);
            statement.setObject(i++, tenant);
            statement.setObject(i++, viewerMembership);
            statement.setObject(i++, farm);
            statement.setObject(i++, otherTenant);
            statement.setObject(i++, outsiderMembership);
            statement.setObject(i++, otherFarm);
            statement.setObject(i++, product);
            statement.setObject(i++, tenant);
            statement.setObject(i++, location);
            statement.setObject(i++, tenant);
            statement.setObject(i++, farm);
            statement.setObject(i++, category);
            statement.setObject(i++, tenant);
            statement.executeUpdate();
        }
    }

    @Test
    void executesTheRepresentativeMvpJourneyThroughHttpSecurityAndRealPostgres() throws Exception {
        String authorization = "Bearer " + token(owner);
        UUID animal = UUID.randomUUID();

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(owner.toString()));
        mvc.perform(get("/api/v1/me/organizations").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].organizationId").value(tenant.toString()));
        mvc.perform(get("/api/v1/me/organizations/{id}/farms", tenant)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].farmId").value(farm.toString()));
        mvc.perform(context(get("/api/v1/context"), authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.id").value(tenant.toString()))
                .andExpect(jsonPath("$.farm.id").value(farm.toString()));

        mvc.perform(context(post("/api/v1/herd/animals"), authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + animal + "\",\"identification\":\"MVP-001\",\"name\":\"Aurora\",\"sex\":\"FEMALE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(animal.toString()));
        mvc.perform(context(get("/api/v1/herd/animals"), authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(context(post("/api/v1/herd/planner-items"), authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"" + UUID.randomUUID() + "\",\"type\":\"VACCINATION\",\"title\":\"Vacinação do lote\",\"scheduledFor\":\"2026-09-15\",\"animalId\":\"" + animal + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mvc.perform(context(post("/api/v1/inventory/movements"), authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"" + UUID.randomUUID() + "\",\"type\":\"RECEIPT\",\"productId\":\"" + product + "\",\"destinationLocationId\":\"" + location + "\",\"quantity\":25,\"occurredOn\":\"2026-09-14\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(25));
        mvc.perform(context(get("/api/v1/inventory/stock"), authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(context(get("/api/v1/inventory/movements"), authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mvc.perform(context(post("/api/v1/finance/entries"), authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"" + UUID.randomUUID() + "\",\"type\":\"INCOME\",\"categoryId\":\"" + category + "\",\"description\":\"Venda do lote\",\"amount\":1200.00,\"dueOn\":\"2026-09-14\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(context(get("/api/v1/finance/summary")
                                .queryParam("from", "2026-09-01")
                                .queryParam("to", "2026-09-30"), authorization))
                .andExpect(status().isOk());
        mvc.perform(context(get("/api/v1/finance/entries")
                                .queryParam("dueFrom", "2026-09-01")
                .queryParam("dueTo", "2026-09-30"), authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mvc.perform(context(get("/api/v1/herd/pending-work"), authorization))
                .andExpect(status().isOk());
        mvc.perform(context(get("/api/v1/herd/dashboard/overview"), authorization))
                .andExpect(status().isOk());
        mvc.perform(context(get("/api/v1/herd/reports/herd-position"), authorization))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MVP-001")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        mvc.perform(get("/api/v1/organizations/{id}/invitations", tenant)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/organizations/{id}/audit", tenant)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
    }

    @Test
    void blocksViewerMutationAndDoesNotRevealAnotherTenant() throws Exception {
        mvc.perform(context(post("/api/v1/herd/animals"), "Bearer " + token(viewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + UUID.randomUUID() + "\",\"identification\":\"NEGADO\",\"sex\":\"MALE\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(context(get("/api/v1/herd/animals"), "Bearer " + token(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Fazenda Horizonte"))))
                .andExpect(content().string(not(containsString(farm.toString()))));
    }

    private MockHttpServletRequestBuilder context(MockHttpServletRequestBuilder request, String authorization) {
        return request.header(HttpHeaders.AUTHORIZATION, authorization)
                .header("X-Organization-Id", tenant)
                .header("X-Farm-Id", farm);
    }

    private static String token(UUID subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience("authenticated")
                .subject(subject.toString())
                .claim("role", "authenticated")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes()));
        return jwt.serialize();
    }
}
