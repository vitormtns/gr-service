# Backend MVP Readiness

Status: **READY** após a conclusão da Phase 11. Esta página é o ponto de entrada operacional; decisões detalhadas permanecem nos ADRs e nos documentos de arquitetura.

## Arquitetura e módulos

O sistema é um monólito modular Spring Boot 3.5/Java 21, stateless, com JDBC explícito e PostgreSQL. Organization é o tenant; uma organization contém fazendas e memberships. Os módulos entregues são Identity, Organizations/Tenant Context, Farms, SaaS Platform Administration, Herd Core/Operations/Intelligence/Reproduction, Planner/Pending Work/Reports/Dashboard, Inventory e Rural Finance.

Os limites são verificados por oito testes ArchUnit. Controllers não acessam JDBC; application não depende de infrastructure; domain não depende de application/infrastructure; integrações entre módulos passam por contratos permitidos.

## Segurança e tenancy

- Supabase Auth fornece JWT; o backend valida o token e sincroniza uma identidade interna desacoplada.
- Papéis de negócio nunca vêm do JWT. Membership, papel, fazenda e escopo são resolvidos no banco em cada requisição.
- O contexto é imutável e existe apenas durante a requisição. Não há sessão, cookie implícito ou `ThreadLocal`.
- Repositories filtram tenant/fazenda e a mesma transação assume `app_api`, instala os settings locais e fica sujeita a RLS forçada.
- Funções `SECURITY DEFINER` têm finalidade limitada, `search_path` fixo e grants explícitos.
- CORS, Actuator e OpenAPI usam defaults restritivos. Segredos vêm do ambiente e falhas são sanitizadas.

O relatório completo está em [Auditoria final da Phase 11](audits/phase11-audit.md).

## Contrato para Portal e Flutter

A API pública usa `/api/v1`. Springdoc deriva a especificação do código e organiza os endpoints por domínio. O perfil `local` habilita `/v3/api-docs/api-v1` e `/swagger-ui.html`; produção os desabilita por padrão, com opt-in explícito por variável.

O contrato de erro comum tem `code`, `message`, `status`, `requestId`, `validationErrors` e `timestamp`. Respostas nunca incluem SQL, constraint, stack trace, token ou evidência de outro tenant. Paginação, datas, números, idempotência e concorrência estão descritos no [guia de consumo](api/consumer-guide.md), e todos os endpoints estão no [inventário](api/endpoint-inventory.md).

## Observabilidade e operação

Cada requisição recebe `X-Request-ID` e `X-Correlation-ID`. IDs fornecidos são aceitos somente em formato restrito; o backend gera UUID quando necessário. O MDC inclui request, correlação e, quando resolvidos com segurança, usuário, tenant e fazenda. Todo MDC é limpo no fim da requisição.

Logs padrão ficam em INFO. Falhas inesperadas registram método, caminho e tipo da exceção, sem cause/payload sensível. Rejeições de negócio esperadas não geram avalanche de ERROR.

Endpoints públicos de probe:

- `/actuator/health/liveness`: processo e estado de liveness, sem depender do banco;
- `/actuator/health/readiness`: estado de readiness e PostgreSQL;
- `/actuator/health`: saúde agregada sem detalhes.

Nenhum outro endpoint Actuator está exposto. O build inclui metadados de nome/versão no artefato, mas o endpoint `info` não é público no MVP.

## Configuração e startup

As propriedades de datasource, role/schema e Supabase JWT já usam configuração tipada e validação antecipada. A Phase 11 adiciona configuração CORS tipada: origins devem ser URLs HTTP(S) absolutas, sem path/query/fragment e sem `*`; credentials e max-age são validados.

Perfis:

- `default`: seguro e sem documentação pública;
- `local`: JWKS local, Swagger/OpenAPI habilitados e origin Angular local explícita;
- `test`: PostgreSQL Testcontainers, HMAC apenas com segredo sintético e documentação para testes de contrato;
- `prod`: logs INFO, health sem detalhes e documentação desligada por padrão.

As variáveis disponíveis estão em `.env.example`. `.env` não é carregado nem versionado automaticamente.

## Migrations e banco

Toda evolução de schema usa migration aditiva do Supabase CLI. A aplicação não cria nem altera DDL no startup. `mvn verify` prova um banco PostgreSQL 15 novo, aplicando todas as migrations em ordem antes dos testes. O inventário de tabelas, funções, RLS, grants e índices está em [Inventário do banco](database/inventory.md).

## Testes e entrega

O gate local e de CI é:

```powershell
.\mvnw.cmd clean verify
git diff --check
```

A suíte inclui unitários, contratos HTTP, serializers estritos, segurança JWT, autorização por papel, repositories, RLS, migrations reais, concorrência/atomicidade, ArchUnit, probes/OpenAPI/CORS e uma jornada MVP entre identidade, contexto, rebanho, planner, inventário, finanças, relatórios e dashboard.

## Limitações conhecidas do MVP

Ficam deliberadamente fora do backend atual: GEDAVE, notificações/push/agendador de e-mail, PDF/Excel, genealogia e protocolos veterinários avançados, BI genérico, analytics preditivo, workflow engine, sync engine genérica, multi-region e decomposição em microservices. Exportações e integrações externas devem nascer de necessidade real dos clientes.

Após a Phase 11, o backend entra em freeze funcional. Novos endpoints serão criados somente por necessidade observada durante Portal/Flutter ou para corrigir bug/gap real. A próxima macroetapa é Portal Angular + aplicativo Flutter.
