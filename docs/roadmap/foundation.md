# Roadmap da fundação

## Fundações concluídas

- Spring Boot com Java 21, Maven Wrapper, Actuator, Validation, scripts locais e CI;
- monólito modular, limites validados por ArchUnit e CQRS lógico no mesmo processo;
- schema privado `app`, migrations versionadas pelo Supabase CLI e validação em PostgreSQL 15 com Testcontainers;
- autenticação JWT do Supabase, identidade interna idempotente e persistência JDBC explícita;
- modelo de organizações, memberships, escopo de fazendas, resolução opt-in de `TenantContext` e transação tenant-aware;
- role `app_api`, RLS, grants mínimos e filtros explícitos por tenant/fazenda;
- leitura e correção concorrente do perfil da fazenda atual com lock otimista e no-op;
- Fase 05A — Herd Read Foundation: migration `app.animals`, listagem paginada de animais, filtros, isolamento multi-tenant, RLS, concorrência e smoke local.
- Fase 05B — Herd Animal Creation: criação idempotente de animal, validação, grants mínimos de INSERT, RLS e concorrência.
- Fase 05C — Herd Animal Profile & Correction: consulta individual e correção parcial com lock otimista, no-op, grants UPDATE por coluna e RLS.

## Próxima fase

As Fases 05A, 05B e 05C já entregam, respectivamente, a listagem do rebanho, a criação de animais e o perfil individual com correção parcial. A vertical atual oferece listagem, criação, `GET` por ID e `PATCH` com lock otimista e no-op; o detalhamento específico permanece no ADR 0020.

## Capacidades posteriores, condicionadas a casos reais

Outbox transacional, eventos de domínio publicados, auditoria de negócio, idempotência para outros comandos, jobs, sincronização offline adicional, réplicas, cache e Event Sourcing seletivo. Cada capacidade deve entrar com consumidor, teste e critério operacional claros.
