# ADR 0018 — Contexto de tenant no escopo da transação PostgreSQL

## Estado da decisão

Aceita.

## Contexto

Após a resolução HTTP autorizar usuário, organização, membership e fazenda, uma operação tenant-aware precisa propagar o `TenantContext` ao PostgreSQL. Role, settings e SQL do repository devem usar a mesma conexão e transação; estado global, configuração fora da transação ou uma conexão auxiliar permitiriam perda ou vazamento de contexto ao reutilizar o pool.

O executor não resolve nem autoriza o tenant. Construir um `TenantContext` manualmente no código interno não é um atalho válido de autorização: o fluxo normal deve usar a instância produzida pelo resolver após as validações de acesso. RLS continua sendo defesa em profundidade e não substitui regras e filtros explícitos da aplicação.

## Decisão

Operações tenant-aware usam explicitamente `TenantTransactionExecutor`. A implementação abre ou reutiliza uma transação com propagação `REQUIRED`, verifica o estado atual e, na mesma conexão, executa `SET LOCAL ROLE app_api`, configura `app.current_user_id` e `app.current_tenant_id` com `set_config(..., true)`, confirma no PostgreSQL a role e os dois UUIDs e só então chama o repository.

Contexto ausente é configurado somente quando a conexão ainda está na role da sessão. Estado parcial, setting inválido, role previamente alterada ou confirmação divergente falha antes do callback. Uma execução aninhada com o mesmo usuário e tenant é idempotente e reutiliza transação, conexão e settings; role diferente de `app_api` é conflito mesmo quando os UUIDs coincidem.

Uma execução aninhada com usuário ou tenant diferente lança conflito antes do callback interno. Como a chamada participa da transação `REQUIRED`, esse rollback marca a transação externa como `rollback-only`; mesmo que o chamador capture o conflito e tente continuar, o commit termina em `UnexpectedRollbackException` e nenhuma escrita é persistida.

## Consequências positivas

- `SET LOCAL` e settings locais são removidos pelo PostgreSQL em commit e rollback, inclusive na reutilização da mesma conexão Hikari.
- A fronteira de segurança é visível no caso de uso e testável sem AOP, anotação mágica ou `ThreadLocal`.
- Falhas de preparação impedem o callback; exceções do repository preservam tipo e identidade.
- Chamadas aninhadas não conseguem trocar silenciosamente usuário, tenant ou role.

## Consequências negativas

- Todo caso de uso tenant-aware deve encaminhar explicitamente o `TenantContext` autorizado ao executor.
- Capturar um conflito aninhado não permite continuar e commitar a operação externa.
- Repositories continuam responsáveis por filtros explícitos de tenant além da proteção RLS.

## Alternativas consideradas

- AOP ou anotação transacional própria: rejeitados por esconderem a fronteira de segurança.
- `ThreadLocal`, sessão ou contexto estático: rejeitados pelo risco de vazamento entre requisições e threads.
- Configuração por conexão auxiliar: rejeitada porque não garante que o repository use a mesma sessão PostgreSQL.
- Configuração de `farmId`: rejeitada nesta fase; a fazenda permanece no `TenantContext` autorizado e não é um setting PostgreSQL.
