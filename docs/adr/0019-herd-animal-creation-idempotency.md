# ADR 0019 — Criação idempotente de animais do rebanho

## Estado da decisão

Aceita para a Fase 05B — Herd Animal Creation. A implementação permanece pendente.

## Contexto

A Fase 05A entregou `app.animals` e `GET /api/v1/herd/animals` com isolamento por tenant e fazenda. O próximo caso de uso é cadastrar um animal exclusivamente na fazenda do `TenantContext` autorizado, sem ampliar o escopo para alteração, remoção, movimentação, reprodução, sanidade, eventos ou auditoria distribuída.

Clientes móveis podem operar com conectividade intermitente e repetir comandos. A criação precisa ser segura para retries, concorrência e isolamento multi-tenant, sem introduzir estado funcional em memória, uma tabela paralela de idempotência ou privilégio de banco excessivo.

## Decisão

### Contrato futuro

O endpoint futuro será `POST /api/v1/herd/animals`. Ele aceitará somente um comando de criação com:

- `id`: obrigatório, UUID e gerado antecipadamente pelo cliente;
- `identification`: obrigatório;
- `name`: opcional e aceitando ausência ou `null`;
- `sex`: obrigatório, com valores `MALE` ou `FEMALE`;
- `birthDate`: opcional.

O servidor não gera UUID nesta fase. O ID não é mutável após a criação.

O comando rejeitará campos desconhecidos e os campos proibidos `tenantId`, `farmId`, `status`, `version`, `createdAt`, `updatedAt` e quaisquer dados de autenticação ou contexto.

`identification` será normalizada removendo apenas whitespace POSIX nas bordas antes da persistência. Whitespace interno e conteúdo significativo serão preservados. O valor persistido será a versão normalizada. Continuam aplicáveis o limite compatível com a migration, a unicidade por tenant/fazenda, a comparação sem diferenciar caixa e a normalização de borda do índice único.

`name` não será convertido silenciosamente de blank para `null`. Quando informado, terá somente whitespace POSIX das bordas removido, preservará whitespace interno e deverá resultar em texto não vazio dentro do limite da migration.

`birthDate`, quando informada, deverá ser menor ou igual à data atual. A fase não definirá idade mínima, idade máxima ou regras zootécnicas adicionais.

O cliente não informa `status` nem `version`. Todo animal novo começa em `ACTIVE` e versão `0`, definidos pelo servidor e pelo schema. `created_at` e `updated_at` permanecem responsabilidade do banco.

### Idempotência por identidade do recurso

O `id` do animal é a chave idempotente. Seu tipo é UUID, seu escopo lógico é global ao recurso e sua retenção é a vida útil do registro. Não haverá header `Idempotency-Key`, `commandId` ou armazenamento paralelo de idempotência nesta fase.

Na primeira execução, se o ID não existir e a identificação não conflitar na fazenda autorizada, a API criará o animal e retornará `201 Created` com a representação pública. Não retornará `Location`, pois não existe `GET` por ID nesta fase.

Em replay com o mesmo ID no mesmo contexto autorizado e comando normalizado semanticamente igual, a API não criará nova linha, não alterará `version` ou timestamps e retornará a representação existente com `200 OK`.

Em replay com o mesmo ID e payload normalizado diferente, a API retornará `409 HERD_IDEMPOTENCY_CONFLICT`, sem sobrescrever o registro. Se outro ID já usar a mesma identificação na mesma fazenda, a API retornará `409 HERD_IDENTIFICATION_CONFLICT`, sem expor dados do animal existente.

Como `app.animals.id` é chave primária global, uma colisão de UUID fora do contexto autorizado não pode revelar tenant, fazenda nem a existência do registro externo. Ela será traduzida pelo mesmo contrato público genérico `409 HERD_IDEMPOTENCY_CONFLICT`, sem indicar se o conflito decorre de registro externo ou de replay divergente.

Essa estratégia atende à ADR 0008: a chave, o escopo, a retenção, o resultado de replay e o conflito divergente são explícitos; o próprio recurso persistido é o estado durável consultado para retry. A PK protege concorrência pelo ID e o índice único de identificação protege a invariável de negócio. Não há incompatibilidade com a ADR 0008; a implementação deverá tratar a corrida de inserção e a leitura de replay na mesma fronteira transacional tenant-aware.

### Resposta e erros públicos

Tanto a criação quanto o replay retornarão apenas `id`, `identification`, `name`, `sex`, `birthDate`, `status` e `version`, sempre com `Cache-Control: no-store`. Não serão expostos tenant, fazenda, timestamps, ator, membership, contexto ou dados JWT.

Os erros públicos da criação serão:

- `400 HERD_COMMAND_INVALID` para comando inválido;
- `409 HERD_IDENTIFICATION_CONFLICT` para identificação já usada na mesma fazenda;
- `409 HERD_IDEMPOTENCY_CONFLICT` para replay divergente ou colisão de UUID não enumerável;
- `503 HERD_PERSISTENCE_UNAVAILABLE` para indisponibilidade técnica sanitizada.

Permanecem responsabilidade da fronteira existente o `401` de autenticação e o contrato não enumerável de contexto tenant/fazenda inacessível. `HERD_QUERY_INVALID` não será reutilizado para escrita.

### Persistência e defesa em profundidade

O caso de uso futuro usará `TenantTransactionExecutor` com o `TenantContext` já autorizado. O repository JDBC usará SQL qualificado e parâmetros preparados, inserindo explicitamente `tenant_id` e `farm_id` exclusivamente do contexto; nunca do cliente.

A futura migration concederá somente `INSERT` necessário a `app_api` e adicionará policy RLS `FOR INSERT` com `WITH CHECK (tenant_id = app.current_tenant_id())`. O `SELECT` já existente permanecerá necessário para leitura e replay. Não será usada função `SECURITY DEFINER`, `service_role`, `BYPASSRLS`, JPA ou qualquer bypass de tenant.

O padrão escolhido é coerente com a persistência JDBC e a transação tenant-aware existentes. A função `SECURITY DEFINER` de bootstrap é uma exceção de leitura anterior à seleção de tenant e não é precedente para escrita operacional.

### Concorrência obrigatória

A implementação deverá provar os seguintes cenários:

- mesmo ID e mesmo payload concorrentes produzem um único animal; um cria e o outro obtém replay;
- mesmo ID e payloads diferentes produzem um vencedor e `HERD_IDEMPOTENCY_CONFLICT` para o outro;
- IDs diferentes e mesma identificação na mesma fazenda produzem um vencedor e `HERD_IDENTIFICATION_CONFLICT` para o outro.

Nenhum cenário pode produzir dois animais equivalentes, resposta `500`, SQL exposto ou vazamento entre tenants/fazendas.

### Auditoria de negócio

Audit trail de negócio não faz parte da Fase 05B. Não serão adicionados `createdBy`, `source`, `deviceId` ou `commandId`. Os timestamps técnicos do banco permanecem suficientes para esta fase.

### Autorização para criação

As chaves de membership existentes são `OWNER`, `ADMIN`, `MANAGER`, `OPERATOR` e `VIEWER`. A documentação informa que são papéis organizacionais, não papéis comerciais, e o `TenantContext` os propaga após validar membership e escopo de fazenda.

Para Herd Animal Creation, a política de aplicação aprovada é:

- `OWNER`: permitido;
- `ADMIN`: permitido;
- `MANAGER`: permitido;
- `OPERATOR`: permitido;
- `VIEWER`: negado.

Essa política vale exclusivamente para a criação de animais e não generaliza capacidades para outros módulos ou operações. O papel `VIEWER` é somente de leitura nesta capability; `OPERATOR` possui capacidade operacional de cadastro; `OWNER`, `ADMIN` e `MANAGER` possuem capacidade de escrita para esta operação.

Quando a identidade estiver autenticada e o `TenantContext` estiver válido, mas o papel for `VIEWER`, o futuro endpoint retornará `403 Forbidden` pelo contrato global de erro já existente. A resposta não devolverá dados de animal, tenant ou fazenda, nem informações adicionais de contexto. Não usará `401`, `404` ou `409`.

A verificação de papel ocorrerá na camada de aplicação antes de qualquer persistência. Ela não dependerá de RLS, não adicionará papel ao SQL de `INSERT` e não substituirá RLS, que continua responsável pelo isolamento de tenant.

## Slices de implementação

### 05B.1 — Contract & domain decisions

Objetivo: materializar o command, regras de normalização, erros e a matriz de autorização aprovada.

Arquivos prováveis: tipos de domínio e aplicação em `modules/herd`, DTOs/handler em `modules/herd/api` e testes unitários de contrato e caso de uso.

Gate: matriz de roles aprovada; campos proibidos, normalização, data futura, estados, respostas e negação `403` documentados em testes.

### 05B.2 — Secure write persistence

Objetivo: criar a migration mínima de `INSERT`, policy RLS e repository JDBC de escrita.

Arquivos prováveis: nova migration em `supabase/migrations`, porta de escrita, adaptador JDBC e testes de migration/integração.

Gate: reset local aplica do zero; `app_api` tem somente o grant adicional necessário; `PUBLIC` não recebe grant; RLS prova ausência de tenant e isolamento A/B.

### 05B.3 — Create use case

Objetivo: executar a criação dentro de `TenantTransactionExecutor` e traduzir resultados de persistência para o domínio.

Arquivos prováveis: command, serviço de aplicação, resultados/exceções e testes unitários.

Gate: tenant/fazenda vêm apenas do `TenantContext`; defaults de status/versão e validações aprovadas; nenhuma escrita fora da transação tenant-aware.

### 05B.4 — HTTP vertical

Objetivo: expor o endpoint com DTO estrito, `201` na criação, `200` no replay e erros públicos sanitizados.

Arquivos prováveis: controller, deserializador/DTO, handler de exceções e testes HTTP verticais.

Gate: `no-store`, contrato público sem campos internos, `401`, `400`, `404` de contexto, `409` e `503` validados.

### 05B.5 — Idempotency & concurrency

Objetivo: provar os três cenários concorrentes definidos nesta ADR.

Arquivos prováveis: testes de integração JDBC e verticais concorrentes do módulo `herd`.

Gate: uma única linha persistida para cada corrida válida; conflitos públicos corretos; nenhum `500` ou vazamento técnico.

### 05B.6 — Smoke & audit

Objetivo: ampliar o smoke local e realizar auditoria da fase.

Arquivos prováveis: `scripts/smoke-auth-local.ps1`, testes de regressão e documentação de auditoria, quando aplicável.

Gate: smoke local real, `mvn verify`, reset local, testes de migração/RLS e `git diff --check` verdes.

## Consequências

- Clientes offline possuem identidade estável antes da primeira transmissão e podem repetir criação sem duplicar o recurso.
- A API precisa comparar comandos normalizados e tratar conflitos de PK e índice único de forma determinística.
- A autorização por papel para criação está definida; a implementação deverá aplicá-la antes da persistência, enquanto o `TenantContext` e RLS preservam as fronteiras de acesso.

## Alternativas consideradas

- UUID gerado pelo servidor: rejeitado nesta fase porque não atende à identidade antecipada para offline e retries.
- Header `Idempotency-Key` e tabela paralela: rejeitados nesta fase porque o UUID obrigatório do recurso já é estado durável, tem retenção natural e cobre o comando de criação.
- Função SQL `SECURITY DEFINER`: rejeitada para a escrita operacional por ampliar privilégio sem necessidade; a transação JDBC com `app_api` e RLS já fornece a fronteira exigida.
- Audit trail, outbox e eventos: adiados por ausência de consumidor ou requisito específico.
