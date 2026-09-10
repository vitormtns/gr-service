# ADR 0020 — Perfil e correção de animais do rebanho

## Estado da decisão

Aceita para a Fase 05C — Herd Animal Profile & Correction.

## Decisão

`GET /api/v1/herd/animals/{id}` lê o animal da fazenda atual para `OWNER`, `ADMIN`, `MANAGER`, `OPERATOR` e `VIEWER`. A leitura é sempre escopada por tenant, fazenda e ID; ausência, tenant externo e fazenda externa retornam `404 HERD_ANIMAL_NOT_FOUND` sem enumeração.

`PATCH /api/v1/herd/animals/{id}` é permitido a `OWNER`, `ADMIN`, `MANAGER` e `OPERATOR`. `VIEWER` recebe `403 HERD_CORRECTION_FORBIDDEN` antes de iniciar transação ou acessar repository. A política é exclusiva desta operação.

O PATCH aceita estritamente `expectedVersion`, `identification`, `name`, `sex` e `birthDate`. `expectedVersion` é obrigatório, não nulo e maior ou igual a zero; pelo menos um campo corrigível deve estar presente. A fronteira JSON distingue ausência de `null`: `name` e `birthDate` podem ser limpos por null; `identification` e `sex` não aceitam null. A normalização e validação são idênticas às da criação.

O update usa optimistic locking com `version` bigint: somente atualiza quando a versão atual coincide com `expectedVersion`, incrementa exatamente uma vez e atualiza `updated_at` no banco. Se o estado normalizado for igual, retorna `200` sem UPDATE, sem alterar versão ou timestamp. Versão divergente retorna `409 HERD_VERSION_CONFLICT`; identificação duplicada na mesma fazenda retorna `409 HERD_IDENTIFICATION_CONFLICT`.

`app_api` recebe somente UPDATE por coluna em `identification`, `name`, `sex`, `birth_date`, `version` e `updated_at`; não recebe UPDATE de chaves, tenant, fazenda, status ou criação. A policy RLS `FOR UPDATE` restringe `USING` e `WITH CHECK` ao tenant transacional, preservando FORCE RLS e os grants existentes.

As respostas públicas são somente a representação do animal e usam `Cache-Control: no-store`. Não expõem tenant, fazenda, timestamps, contexto, versão vencedora nem detalhes de SQL.

## Fora de escopo

Delete, arquivamento, alteração de status, morte, venda, transferência, movimentação, reprodução, saúde, histórico de auditoria, eventos de domínio e outbox.
