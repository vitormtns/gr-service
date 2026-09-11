# Roadmap da fundação

## Fase 05E concluída

- Fase 05E — Farm Territory & Herd Movement Core: piquetes, localização atual, movimentação individual e em lote, ocupação, timeline e log operacional com isolamento por tenant/fazenda.

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
- Fase 05D — Herd Lifecycle & Activity Timeline: transições SOLD/DECEASED, timeline operacional append-only, idempotência, concorrência e isolamento por tenant.

## Próxima fase

As Fases 05A a 05D entregam listagem, criação idempotente, consulta e correção parcial com lock otimista/no-op, além de transições SOLD/DECEASED e timeline operacional append-only. O detalhamento da timeline e do lifecycle permanece no ADR 0021.

## Capacidades posteriores, condicionadas a casos reais

Outbox transacional, eventos de domínio publicados, auditoria de negócio, idempotência para outros comandos, jobs, sincronização offline adicional, réplicas, cache e Event Sourcing seletivo. Cada capacidade deve entrar com consumidor, teste e critério operacional claros.
# Fase 05F concluída — transferência de rebanho entre fazendas e cadeia de custódia implementadas. O próximo foco volta ao MVP geral fora do aprofundamento de rebanho.
