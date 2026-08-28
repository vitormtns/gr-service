# gr-service

Backend do projeto provisoriamente chamado **Gerenciador Rural**. Este repositório contém a fundação técnica de uma API SaaS multi-tenant, organizada como monólito modular e preparada para crescer sem antecipar infraestrutura distribuída.

## Estado atual

A aplicação Spring Boot valida access tokens do Supabase Auth e expõe endpoints protegidos para identidade, organizações, fazendas acessíveis e resolução opt-in do contexto. O UUID validado de `sub` é sincronizado com `app.users` por JDBC explícito, de forma idempotente e concorrente. `GET /actuator/health` permanece público; os demais caminhos exigem autenticação ou ficam bloqueados.

## Arquitetura resumida

- Monólito modular orientado por domínio, com limites verificados por ArchUnit.
- Separação lógica de escrita e leitura, sem command bus ou query bus.
- Tenant definido por `Organization`; uma organização pode ter várias fazendas.
- API stateless, pronta para múltiplas instâncias e workers futuros.
- PostgreSQL gerenciado por migrations do Supabase CLI, com JDBC explícito e sem alteração automática de schema.
- Eventos de domínio versionados, com outbox e Event Sourcing apenas como decisões futuras e seletivas.

Leia [a visão arquitetural](docs/architecture/overview.md) e os [ADRs](docs/adr/) antes de alterar decisões estruturais.

## Requisitos

- Java 21;
- acesso à internet na primeira execução do Maven Wrapper;
- Node.js e npm para as ferramentas locais de infraestrutura;
- Docker Desktop com backend WSL 2 para operar o PostgreSQL local e executar os testes Testcontainers.

Não é necessário instalar Maven nem Supabase CLI globalmente. O Wrapper usa Maven 3.9.16 e a CLI do Supabase está fixada como `devDependency` no `package.json`. A API usa um `DataSource` Hikari em runtime. `mvn verify` exige Docker porque os testes aplicam as migrations reais em PostgreSQL isolado com Testcontainers.

## Preparação do ambiente

1. Copie `.env.example` para `.env` somente se precisar documentar valores locais. O Spring não carrega `.env` automaticamente.
2. Execute `npm ci` para instalar a versão exata das ferramentas registrada em `package-lock.json`.
3. Defina `SPRING_PROFILES_ACTIVE=local` no ambiente.
4. Quando precisar do banco local, inicie o Docker Desktop e execute `npm run supabase:start`.

Variáveis documentadas em `.env.example`:

| Variável | Finalidade |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Seleciona o perfil Spring; use `local` no desenvolvimento. |
| `SERVER_PORT` | Porta HTTP, com padrão local `8080`. |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Conexão JDBC obrigatória; use um login runtime dedicado e nunca versione a senha. |
| `DATABASE_SCHEMA`, `DATABASE_RUNTIME_ROLE` | Limites fixos validados como `app` e `app_api`. |
| `DATABASE_POOL_MAX_SIZE`, `DATABASE_POOL_MIN_IDLE` | Tamanho do pool Hikari por instância. |
| `DATABASE_CONNECTION_TIMEOUT` | Timeout do pool em milissegundos. |
| `SUPABASE_AUTH_MODE` | Modo explícito de validação: `JWKS` ou `HMAC`. O perfil local usa `JWKS`. |
| `SUPABASE_AUTH_ALGORITHM` | Algoritmo único aceito: `ES256` ou `RS256` em JWKS; `HS256` em HMAC. |
| `SUPABASE_AUTH_ISSUER` | Emissor exato esperado no claim `iss`. |
| `SUPABASE_AUTH_JWKS_URI` | Endpoint público de chaves no modo JWKS. |
| `SUPABASE_AUTH_JWT_SECRET` | Segredo somente de backend para HMAC legado; nunca o versione nem o envie a clientes. |
| `SUPABASE_AUTH_AUDIENCES`, `SUPABASE_AUTH_TOKEN_ROLES` | Audiences e roles técnicas aceitas, separadas por vírgula. |
| `SUPABASE_AUTH_CLOCK_SKEW` | Tolerância temporal pequena, com padrão de `30s`. |

Nunca versione `.env`, senhas, tokens, `service_role` ou chaves secretas.

## Execução local

O login de conexão deve ser `NOINHERIT`, sem privilégios diretos, e possuir apenas membership em `app_api`. A aplicação executa `SET LOCAL ROLE app_api` dentro da transação; não use `postgres`, `service_role`, superusuário ou `BYPASSRLS` em runtime. No PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:54322/postgres"
$env:DATABASE_USERNAME = "<login-runtime-local>"
$env:DATABASE_PASSWORD = "<senha-local-não-versionada>"
.\mvnw.cmd spring-boot:run
```

Em Bash:

```bash
SPRING_PROFILES_ACTIVE=local DATABASE_URL=jdbc:postgresql://127.0.0.1:54322/postgres \
DATABASE_USERNAME=<login-runtime-local> DATABASE_PASSWORD=<senha-local-não-versionada> \
./mvnw spring-boot:run
```

Verifique a aplicação em `http://localhost:8080/actuator/health`. O Actuator expõe somente o endpoint de saúde e não mostra detalhes publicamente. O perfil `local` já aponta para o issuer e o JWKS do Supabase CLI em `127.0.0.1`; nenhum segredo HMAC é necessário no stack local atual.

## Autenticação HTTP

O fluxo é `Supabase Auth -> access token JWT -> Spring Security Resource Server -> AuthenticatedUser`. O Spring valida assinatura, algoritmo, issuer, expiração, `nbf` quando presente, `iat`, audience, role técnica e o UUID de `sub`. Claims desconhecidos são ignorados e o bearer token não é armazenado nem retornado.

O modo `JWKS` é preferencial para emissores com chaves assimétricas e suporta `ES256` ou `RS256` configurado explicitamente. O modo `HMAC` aceita somente `HS256`, exige segredo de pelo menos 32 bytes no backend e existe para projetos legados; não há fallback automático entre os modos. O stack local validado publica uma chave `ES256` em `/auth/v1/.well-known/jwks.json`.

`GET /api/v1/me` executa `JWT -> AuthenticatedUser -> SynchronizeAuthenticatedUser -> app.users`. O UUID de `sub` é a chave interna; e-mail válido pode ser sincronizado, mas ausência ou valor inválido nunca apaga o valor persistido. Não existe claim confiável de nome no contrato atual, portanto `displayName` é preservado e não é atualizado pelo token. Chamadas sem mudança preservam `version` e `updatedAt`; updates reais usam locking otimista e retries limitados. Usuários `SUSPENDED` ou `DEACTIVATED` recebem `403` e nunca são reativados pelo JWT.

`GET /api/v1/me/organizations` executa `JWT -> AuthenticatedUser -> SynchronizeAuthenticatedUser -> app.current_user_id transacional -> app.list_current_user_organizations()`. A resposta é `{ "items": [] }` quando não há memberships acessíveis e cada item contém somente `organizationId`, `organizationName`, `membershipId`, `role` e `farmScopeMode`. A função de bootstrap não recebe `userId`, não retorna fazendas e não cria `TenantContext`; `role` vem de `organization_memberships.role_key`, nunca do JWT. A listagem de fazendas e a resolução do contexto ocorrem em endpoints separados, sempre com validação no banco.

`GET /api/v1/me/organizations/{organizationId}/farms` valida o acesso à organização usando o usuário autenticado, sem confiar no UUID informado pela URL. Para `ALL_FARMS`, retorna todas as fazendas `ACTIVE` da organização; para `SELECTED_FARMS`, somente as fazendas `ACTIVE` vinculadas ao mesmo membership. Organizações inacessíveis e estados bloqueados resultam em `{ "items": [] }`, sem revelar a existência do recurso. Esse endpoint lista opções; a resolução validada do contexto ocorre em `/api/v1/context`.

`GET /api/v1/context` é opt-in e exige os headers `X-Organization-Id` e `X-Farm-Id`. Os valores são apenas uma solicitação: a API valida usuário, organização, membership, fazenda e escopo antes de devolver o contexto. O `TenantContext` é imutável, armazenado somente como atributo da requisição e removido naturalmente ao término dela; não há sessão, cookie, `ThreadLocal`, cache global ou configuração de `app.current_tenant_id`. Headers inválidos retornam `400`, combinação inacessível retorna `404` e toda resposta válida usa `Cache-Control: no-store`.

A resposta separa identidade persistida (`userId`, `email`, `displayName`, `status`, timestamps e `version`) de `authentication` (`sessionId`, `authenticationLevel`, `issuedAt` e `expiresAt`). Ela não retorna token, claims completos, organizações, fazendas ou memberships.

Sem bearer token válido, `/api/**` responde `401` em JSON. Um usuário autenticado que alcançar uma regra negada recebe `403`, também em JSON. As respostas incluem o request ID quando disponível e não revelam detalhes criptográficos.

Para um projeto remoto futuro, configure issuer, JWKS URI e algoritmo conforme as chaves efetivamente publicadas pelo projeto, sem incluir project ref real neste repositório. Consulte a [documentação oficial do Spring Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) e a [documentação de JWTs do Supabase](https://supabase.com/docs/guides/auth/jwts).

## Testes e validação arquitetural

No Windows:

```powershell
.\mvnw.cmd verify
.\scripts\check.ps1
```

Em Bash:

```bash
./mvnw verify
./scripts/check.sh
```

O mesmo comando compila a aplicação, executa JUnit 5, valida os limites com ArchUnit e empacota o JAR.

Os testes de integração iniciam um PostgreSQL 15 isolado, aplicam diretamente os arquivos reais de `supabase/migrations` em ordem e encerram o container automaticamente. Eles não dependem da stack local do Supabase.

Os scripts aceitam verificações locais opcionais sem reset implícito:

```powershell
.\scripts\check.ps1 -SupabaseStatus
.\scripts\check.ps1 -SupabaseReset
```

```bash
./scripts/check.sh --supabase-status
./scripts/check.sh --supabase-reset
```

Após `mvn verify`, o smoke test opcional cria credenciais de Auth e um login PostgreSQL efêmeros, inicia uma API temporária, confirma persistência, idempotência e isolamento de `GET /api/v1/me/organizations` e não imprime tokens, senhas ou chaves. O banco local é resetado ao final:

```powershell
.\scripts\smoke-auth-local.ps1
```

Esse smoke não faz parte da CI e nunca acessa um projeto remoto.

## Supabase local e migrations

Com Docker Desktop em execução e as dependências instaladas por `npm ci`:

```bash
npm run supabase:version
npm run supabase:start
npm run supabase:status
npm run supabase:reset
npm run supabase:stop
npx supabase migration new nome_da_migration
```

Detalhes estão em [supabase/README.md](supabase/README.md). Toda alteração estrutural deve ser feita por migration versionada. Não altere tabelas de negócio manualmente pelo painel e não use `supabase db push` sem um fluxo de entrega aprovado. Nenhuma migration ornamental foi criada nesta fundação.

## Identidade e tenancy

A organização é o tenant; fazendas e memberships pertencem obrigatoriamente a uma organização. Usuários são identidades globais e podem participar de vários tenants. O UUID de `app.users` corresponde ao claim validado `sub` do Supabase Auth, sem FK para `auth.users` e sem armazenar senhas ou tokens. A PK protege a primeira sincronização concorrente e a coluna `version` protege atualizações concorrentes.

As tabelas de negócio ficam no schema privado `app`, fora dos schemas expostos pelo PostgREST. Clientes acessam o domínio exclusivamente pela API Spring. A role `app_api` e as policies RLS usam um tenant configurado localmente por transação como defesa adicional, sem substituir os futuros filtros tenant-aware dos repositories.

## Contexto transacional de tenant

A resolução HTTP autoriza e produz um `TenantContext` imutável, mas não configura o tenant no banco. Casos de uso tenant-aware devem encaminhar essa mesma instância a `TenantTransactionExecutor.execute(context, () -> repository.operacao())`. O executor apenas propaga um contexto já autorizado: construir `TenantContext` manualmente não é caminho de autorização. Na mesma conexão e transação PostgreSQL, ele executa `SET LOCAL ROLE app_api`, configura `app.current_user_id` e `app.current_tenant_id` com `set_config(..., true)`, confirma o estado no banco e só então chama o repository. Commit e rollback removem automaticamente role e settings locais.

Execuções aninhadas com o mesmo usuário e tenant são idempotentes; outro usuário ou tenant falha antes do callback interno e marca a transação `REQUIRED` como `rollback-only`, mesmo quando o conflito é capturado. O executor não usa AOP, anotação transacional mágica, `ThreadLocal`, conexão auxiliar ou setting global de fazenda: `farmId` permanece apenas no `TenantContext`. Repositories futuros devem manter filtros explícitos por tenant e executar dentro do executor; RLS é uma segunda linha de defesa.

Leia o [modelo de identidade e tenancy](docs/architecture/identity-tenancy-data-model.md) para conhecer tabelas, relações, índices e camadas de segurança.

## Estrutura

```text
src/main/java/com/gerenciadorrural/
├── GerenciadorRuralApplication.java
├── modules/identity/        # identidade interna persistida
└── shared/
    ├── api/                 # convenções HTTP compartilhadas
    ├── application/         # contratos de command e query
    ├── domain/              # conceitos de domínio transversais
    ├── infrastructure/      # adaptadores técnicos compartilhados
    ├── observability/       # contexto de diagnóstico
    ├── security/            # identidade autenticada e fronteira JWT
    └── tenancy/             # identidade e contexto do tenant
docs/                        # arquitetura, ADRs e roadmap
supabase/                    # configuração, migrations e seed local
scripts/                     # validação para Windows e Bash
```

O primeiro módulo real é `modules/identity/{domain,application,infrastructure,api}`. Módulos futuros seguem a mesma convenção, com `application/command` e `application/query` quando houver casos de uso. Não crie módulos ou camadas vazias apenas para completar a árvore.

## Banco e acesso ao domínio

O schema pertence às migrations do Supabase CLI. O primeiro repository usa `NamedParameterJdbcTemplate`, mapeamento manual e SQL qualificado em `app.users`. JPA, Hibernate e `ddl-auto` não fazem parte da aplicação.

Flutter e Angular não podem escrever diretamente nas tabelas de negócio. Clientes chamam a API, que autentica o bearer token e sincroniza a identidade interna; as próximas etapas resolverão tenant, fazenda, permissões, capacidades e cotas. RLS será uma defesa complementar, não um substituto das regras da API.
