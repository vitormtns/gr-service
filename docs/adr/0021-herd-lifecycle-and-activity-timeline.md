# ADR 0021 — Ciclo de vida e timeline de atividade do rebanho

## Decisão

`app.animals` continua sendo o estado autoritativo. `app.animal_events` é um histórico operacional append-only, não Event Sourcing, CQRS físico, outbox ou barramento de eventos.

Os estados existentes do domínio são `ACTIVE`, `SOLD`, `DECEASED`, `TRANSFERRED` e `ARCHIVED`. Nesta fase apenas as novas transições `ACTIVE -> SOLD` e `ACTIVE -> DECEASED` são implementadas; `TRANSFERRED` e `ARCHIVED` continuam representáveis, mas fora do escopo operacional. Não há reativação ou undo. Correções cadastrais continuam permitidas em estados terminais.

Os endpoints são `POST /api/v1/herd/animals/{id}/sale`, `POST /api/v1/herd/animals/{id}/death` e `GET /api/v1/herd/animals/{id}/history`. Venda é permitida para OWNER, ADMIN e MANAGER; morte também permite OPERATOR; a leitura permite todos os papéis. A autorização ocorre antes do acesso ao recurso.

Comandos lifecycle exigem `operationId`, `expectedVersion` e `occurredOn`, com `notes` opcional. A idempotência é escopada por tenant, fazenda e operationId; um advisory transaction lock serializa o mesmo operationId. Replay semanticamente igual retorna o resultado já gravado; divergência retorna `HERD_OPERATION_IDEMPOTENCY_CONFLICT`.

Para uma operação nova, a prioridade é: replay, lock do animal, versão esperada e estado lifecycle. Assim uma versão stale em estado terminal retorna `HERD_VERSION_CONFLICT`. Atualização de estado e evento são realizados na mesma transação tenant-aware.

Os eventos incluem CREATED, CORRECTED, SOLD e DECEASED, ator, versão resultante, data de ocorrência e detalhes. CREATED registra snapshot público; CORRECTED contém somente before/after realmente alterados. A timeline usa `recorded_at DESC, id DESC`, paginação e filtro por tipo. `app_api` só tem SELECT e INSERT; RLS e FORCE RLS preservam isolamento de tenant.

O backfill cria um único CREATED por animal existente, com ator nulo, `created_at` como registro e versão 0. Correções anteriores à 05D não podem ser reconstruídas de modo fiel e não são fabricadas; o histórico é completo prospectivamente.

## Fora de escopo

Transferência, comprador, preço, financeiro, reprodução, saúde, anexos, notificações, hard delete, reativação, outbox e Event Sourcing.
