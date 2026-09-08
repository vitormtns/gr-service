# Roadmap da fundação

## Fundações concluídas

- Spring Boot com Java 21, Maven Wrapper, Actuator, Validation, scripts locais e CI;
- monólito modular, limites validados por ArchUnit e CQRS lógico no mesmo processo;
- schema privado `app`, migrations versionadas pelo Supabase CLI e validação em PostgreSQL 15 com Testcontainers;
- autenticação JWT do Supabase, identidade interna idempotente e persistência JDBC explícita;
- modelo de organizações, memberships, escopo de fazendas, resolução opt-in de `TenantContext` e transação tenant-aware;
- role `app_api`, RLS, grants mínimos e filtros explícitos por tenant/fazenda;
- leitura e atualização concorrente do perfil da fazenda atual;
- Fase 05A — Herd Read Foundation: migration `app.animals`, listagem paginada de animais, filtros, isolamento multi-tenant, RLS, concorrência e smoke local.

## Próxima fase

### 05B — Herd Animal Creation

Implementar exclusivamente a criação idempotente de animal na fazenda autorizada pelo `TenantContext`, por meio do futuro `POST /api/v1/herd/animals`.

A fase deve definir e validar o comando de criação, UUID fornecido antecipadamente pelo cliente, normalização de dados, conflitos de identificação, idempotência por identidade do recurso, persistência JDBC tenant-aware, `INSERT` mínimo para `app_api`, RLS de escrita, endpoint HTTP, concorrência e smoke local.

Ficam fora de escopo: atualização, remoção, consulta por ID, movimentação, reprodução, sanidade, eventos, outbox e auditoria distribuída.

A matriz de autorização da criação está definida para esta capability: `OWNER`, `ADMIN`, `MANAGER` e `OPERATOR` podem criar; `VIEWER` recebe `403 Forbidden`. A regra é aplicada na camada de aplicação antes da persistência e não generaliza capacidades para outros módulos.

## Capacidades posteriores, condicionadas a casos reais

Outbox transacional, eventos de domínio publicados, auditoria de negócio, idempotência para outros comandos, jobs, sincronização offline adicional, réplicas, cache e Event Sourcing seletivo. Cada capacidade deve entrar com consumidor, teste e critério operacional claros.
