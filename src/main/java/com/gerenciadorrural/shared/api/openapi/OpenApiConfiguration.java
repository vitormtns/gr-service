package com.gerenciadorrural.shared.api.openapi;

import com.gerenciadorrural.shared.api.error.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.Arrays;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String BEARER = "bearer-jwt";
    private static final String ERROR_SCHEMA = "#/components/schemas/ApiErrorResponse";

    @Bean
    OpenAPI gerenciadorRuralOpenApi() {
        Components components = new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Access token JWT validado pelo backend. Use: Bearer <token>."));
        ModelConverters.getInstance().read(ApiErrorResponse.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("API Gerenciador Rural")
                        .version("v1")
                        .description("Contrato da API para o Portal Angular e o aplicativo Flutter. "
                                + "Os clientes nunca acessam diretamente as tabelas de negócio."))
                .components(components)
                .tags(List.of(
                        tag("Identity", "Identidade autenticada."),
                        tag("Organizations", "Organizações, memberships, convites e contexto."),
                        tag("Farms", "Fazenda atual."),
                        tag("Herd", "Cadastro e operações de rebanho."),
                        tag("Planner", "Planejamento operacional."),
                        tag("Agenda", "Agenda operacional consolidada."),
                        tag("Pending Work", "Trabalho pendente derivado."),
                        tag("Reports", "Relatórios pecuários."),
                        tag("Dashboard", "Dashboard e insights."),
                        tag("Inventory", "Produtos, locais, saldo e movimentos de estoque."),
                        tag("Finance", "Categorias, lançamentos e resumos financeiros."),
                        tag("Platform", "Controle administrativo SaaS.")));
    }

    @Bean
    GroupedOpenApi publicApiV1(OperationCustomizer apiContractCustomizer) {
        return GroupedOpenApi.builder()
                .group("api-v1")
                .pathsToMatch("/api/v1/**")
                .addOperationCustomizer(apiContractCustomizer)
                .build();
    }

    @Bean
    OperationCustomizer apiContractCustomizer() {
        return (operation, handlerMethod) -> {
            operation.setTags(List.of(tagFor(handlerMethod.getBeanType(), handlerMethod.getMethod().getName())));
            operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
            addResponse(operation, "400", "Solicitação inválida");
            addResponse(operation, "401", "Autenticação ausente ou inválida");
            addResponse(operation, "403", "Operação não autorizada");
            addResponse(operation, "500", "Falha interna sanitizada");
            addResponse(operation, "503", "Dependência temporariamente indisponível");

            boolean tenantAware = Arrays.stream(handlerMethod.getMethodParameters())
                    .flatMap(parameter -> Arrays.stream(parameter.getParameterAnnotations()))
                    .anyMatch(annotation -> annotation.annotationType().getName().equals(
                            "com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext"));
            if (tenantAware) {
                operation.addParametersItem(contextHeader(
                        "X-Organization-Id",
                        "UUID da organização solicitada. O backend valida membership e estado atual."));
                operation.addParametersItem(contextHeader(
                        "X-Farm-Id",
                        "UUID da fazenda solicitada. Obrigatório nos endpoints com contexto de fazenda."));
                addResponse(operation, "404", "Recurso ou contexto não disponível");
            }
            if (isMutation(handlerMethod.getMethod())) {
                addResponse(operation, "409", "Conflito de negócio, idempotência ou versão");
            }
            addUsefulExample(operation, handlerMethod.getBeanType(), handlerMethod.getMethod().getName());
            return operation;
        };
    }

    private static void addResponse(io.swagger.v3.oas.models.Operation operation, String status, String description) {
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref(ERROR_SCHEMA)))));
    }

    private static Parameter contextHeader(String name, String description) {
        return new Parameter()
                .name(name)
                .in("header")
                .required(true)
                .description(description)
                .schema(new Schema<String>().type("string").format("uuid")
                        .example("2a7fcb65-c4f8-47af-a903-9b8d30d73e42"));
    }

    private static boolean isMutation(java.lang.reflect.Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class);
    }

    private static String tagFor(Class<?> controller, String method) {
        String name = controller.getSimpleName();
        if (name.equals("CurrentUserController")) return "Identity";
        if (name.equals("CurrentUserOrganizationsController") || name.equals("TenantContextController")) {
            return "Organizations";
        }
        if (name.equals("PlatformAdministrationController")) return "Platform";
        if (name.equals("FarmProfileController")) return "Farms";
        if (name.equals("InventoryController")) return "Inventory";
        if (name.equals("FinanceController")) return "Finance";
        if (name.equals("HerdPlannerController")) return "Planner";
        if (name.equals("HerdAgendaController")) return "Agenda";
        if (name.equals("HerdIntelligenceController") && method.equals("pending")) return "Pending Work";
        if (name.equals("HerdReportController")) return "Reports";
        if (name.equals("HerdDashboardController")) return "Dashboard";
        return "Herd";
    }

    private static void addUsefulExample(io.swagger.v3.oas.models.Operation operation, Class<?> controller, String method) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) return;
        MediaType json = operation.getRequestBody().getContent().get("application/json");
        if (json == null) return;

        String name = controller.getSimpleName();
        if (name.equals("HerdAnimalController") && method.equals("create")) {
            json.setExample(Map.of(
                    "id", "d31ad9b5-fc8d-4e55-8a49-d7ca5ae2cae2",
                    "identification", "BR-2026-001",
                    "name", "Aurora",
                    "sex", "FEMALE",
                    "birthDate", "2024-03-15"));
        } else if (name.equals("InventoryController") && method.equals("move")) {
            json.setExample(Map.of(
                    "operationId", "91bd35a8-5c90-4dfc-8cf8-2efb7c46f397",
                    "type", "RECEIPT",
                    "productId", "9e6b07c3-5b8c-4654-af2d-428d04b72cdb",
                    "destinationLocationId", "e9749e1f-510a-4261-a09e-978465ebc934",
                    "quantity", 25,
                    "occurredOn", "2026-09-14"));
        } else if (name.equals("FinanceController") && method.equals("create")) {
            json.setExample(Map.of(
                    "operationId", "4259cbd8-9014-4147-a31c-9483ff1cab92",
                    "type", "INCOME",
                    "categoryId", "22e70fd5-a56d-478d-ad7f-911c5831f74c",
                    "description", "Venda do lote",
                    "amount", 1200.00,
                    "dueOn", "2026-09-14"));
        } else if (name.equals("HerdPlannerController") && method.equals("create")) {
            json.setExample(Map.of(
                    "operationId", "848ad781-b38b-423d-94a1-0f1fbfa8d742",
                    "type", "VACCINATION",
                    "title", "Vacinação do lote",
                    "scheduledFor", "2026-09-15"));
        }
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
