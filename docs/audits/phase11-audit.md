# Auditoria final da Phase 11

Data: 14/09/2026. Escopo: contrato HTTP, arquitetura, autorização, tenancy, SQL, migrations, dependências e prontidão operacional do Backend MVP.

## Resultado

| Severidade | Abertos | Resultado |
| --- | ---: | --- |
| CRITICAL | 0 | nenhum |
| HIGH | 0 | nenhum |
| MEDIUM | 0 | nenhum |
| LOW | 3 | limitações aceitáveis e documentadas abaixo |

## Achados corrigidos

- **MEDIUM — consultas opcionais sem tipo SQL:** filtros nulos de inventário, finanças, plataforma e pendências podiam produzir `BadSqlGrammarException` no PostgreSQL. Os parâmetros agora têm casts explícitos e a jornada smoke cobre leitura sem filtros.
- **MEDIUM — advice de persistência transversal:** um handler de identidade podia rotular falhas de outro módulo como erro de identidade. O advice foi limitado aos controllers de identidade/bootstrap e os handlers de módulo receberam precedência determinística.
- **MEDIUM — erro inesperado e de persistência sem fallback uniforme:** foi adicionado fallback sanitizado para `400`, `500` e `503`, sempre com envelope comum e `no-store`, sem mensagem/cause/stack na resposta ou no log operacional.
- **LOW — índice financeiro redundante:** o índice explícito de `operationId` duplicava o índice da restrição única. Foi removido em nova migration e há detecção automática de duplicatas estruturais.
- **LOW — correlação parcial:** a resposta devolvia somente request ID. Agora devolve request e correlation IDs, aceita apenas formato seguro, inclui IDs de usuário/tenant/fazenda no MDC quando disponíveis e sempre limpa o contexto.

## Segurança

- JWT: modos JWKS/HMAC explícitos; algoritmo fixo; assinatura, issuer, audience, expiração, `nbf`, `iat`, role técnica e UUID de `sub` validados. Swagger não contorna autenticação.
- Autorização: matriz representativa OWNER/ADMIN/MANAGER/OPERATOR/VIEWER revisada em platform, herd, planner, inventory e finance. Smoke prova escrita autorizada, mutação VIEWER negada e não enumeração entre tenants.
- Tenancy: contexto imutável e request-scoped; sem sessão, cookie ou `ThreadLocal`; resolução revalida membership, papel, escopo e estados; transação instala role, usuário e tenant localmente.
- RLS/SECURITY DEFINER/grants: inventário automatizado em PostgreSQL real; funções elevadas têm `search_path` fixo e sem `PUBLIC EXECUTE`; role runtime sem privilégios administrativos.
- Mass assignment: comandos sensíveis usam DTOs/deserialize estritos; tenant, fazenda, ator, timestamps e versão resultante permanecem server-owned.
- SQL injection: valores usam bind parameters; não foi encontrada ordenação dinâmica baseada em texto do cliente. Busca `LIKE` escapa curingas.
- Segredos/logs: nenhum segredo real versionado. Ocorrências de `password=secret` são cargas sintéticas que provam sanitização. Authorization, JWT, token de convite e payload pessoal não são registrados pela aplicação.
- Superfícies operacionais: somente `health` está exposto pelo Actuator; `env`, `beans`, `configprops`, `mappings` e métricas não são públicos. OpenAPI/Swagger estão desligados por padrão e no perfil `prod`.
- CORS: origins explícitas e tipadas, sem curinga; credentials desabilitadas por padrão; methods/headers restritos.

## Contratos e qualidade

- Todos os endpoints públicos estão sob `/api/v1`; inventário completo em `docs/api/endpoint-inventory.md`.
- Paginação é zero-based, limitada e com ordenação estável. Listas não paginadas são catálogos/opções naturalmente pequenos no MVP.
- Datas civis usam `LocalDate`, instantes usam `Instant`/`OffsetDateTime`, fatos numéricos precisos usam `BigDecimal`/`NUMERIC`; não há `float`/`double` nos fatos auditados.
- Idempotência e concorrência permanecem cobertas onde há risco material: ciclo de vida, movimentos, transferências, inteligência, reprodução, inventário, finanças, planner e administração.
- Workflows multi-write executam em fronteiras transacionais e têm provas de rollback/replay/concorrência existentes.
- A busca por `TODO`, `FIXME`, `HACK` e `XXX` não encontrou blocker ou workaround temporário no código-fonte.
- As oito regras ArchUnit cobrem controllers sem JDBC, dependências application/domain/infrastructure e limites entre módulos; nenhuma regra ornamental foi adicionada.

## Banco e desempenho

- As 30 tabelas, oito funções e famílias de índices estão registradas em `docs/database/inventory.md`.
- Todas as migrations históricas são preservadas; a correção do índice redundante é aditiva.
- Leituras críticas de rebanho, agenda, pendências, relatórios, dashboard, saldo/razão de estoque, resumos financeiros e administração foram confrontadas com os índices existentes e exercitadas em PostgreSQL real.
- Não foi identificado N+1 significativo; agregações e páginas são executadas por consultas set-based. Contagens de inventário/finanças ainda reutilizam consultas limitadas em vez de `count(*)`, classificadas como LOW pelo limite de 100.000 registros e pelo estágio MVP.
- `EXPLAIN ANALYZE` em bases mínimas seria dominado por sequential scan e não forneceria evidência representativa. O risco de plano deve ser reavaliado com distribuição e volume reais após telemetria dos clientes.

## Dependências e CI

- `mvn dependency:analyze` concluiu com sucesso. Seus avisos são o comportamento conhecido de starters agregadores do Spring Boot/ArchUnit e não justificam quebrar o gerenciamento de dependências.
- `npm audit --omit=dev --audit-level=moderate` encontrou 0 vulnerabilidades de runtime. Não existe scanner Maven/CVE confiável já integrado; adicionar uma ferramenta pesada ficou fora do escopo.
- A CI usa checkout, Temurin 21, cache Maven, Maven Wrapper e `verify`; permissões são `contents: read`, há timeout e cancelamento de runs obsoletos por ref.

## Limitações LOW aceitas

1. Contagens de estoque e finanças materializam até 100.000 linhas; substituir por `count(*)` dedicado quando telemetria indicar crescimento relevante.
2. Catálogos de produtos, locais e categorias não são paginados; são listas pequenas no MVP e uma mudança agora seria incompatível sem benefício medido.
3. Não há scan Maven de CVEs na CI; adotar solução organizacional com política de supressão e atualização quando o projeto definir sua plataforma de segurança.

Nenhuma limitação aberta é blocker de segurança ou correctness para o Backend MVP.
