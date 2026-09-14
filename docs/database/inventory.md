# Inventário do banco do Backend MVP

O schema privado `app` nasce exclusivamente pelas migrations versionadas em `supabase/migrations`. Clientes não recebem acesso direto. A conexão da aplicação usa um login `NOINHERIT` e assume `app_api` somente dentro da transação tenant-aware.

## Tabelas por domínio

| Domínio | Tabelas |
| --- | --- |
| Identidade e tenancy | `users`, `organizations`, `farms`, `organization_memberships`, `membership_farm_scopes` |
| Rebanho e território | `animals`, `animal_events`, `paddocks`, `herd_movements`, `herd_movement_operations`, `animal_transfers`, `herd_transfer_operations` |
| Inteligência e reprodução | `herd_management_operations`, `animal_weight_measurements`, `animal_health_treatments`, `animal_pregnancies`, `animal_maternal_relations` |
| Planner | `herd_planner_items`, `herd_planner_operations` |
| Inventário | `inventory_products`, `inventory_locations`, `inventory_balances`, `inventory_operations`, `inventory_movements` |
| Finanças | `financial_categories`, `financial_entries`, `financial_entry_events` |
| Plataforma | `organization_invitations`, `organization_invitation_farm_scopes`, `platform_admin_events` |

São 30 tabelas. `users` é a única identidade global; todas as tabelas que possuem `tenant_id` têm RLS habilitada e forçada. `organizations` representa o próprio tenant e também tem política própria. FKs compostas preservam tenant/fazenda nas associações sensíveis.

## Funções

| Função | SECURITY DEFINER | Justificativa e restrições |
| --- | --- | --- |
| `current_user_id()` | não | lê apenas o setting transacional do usuário |
| `current_tenant_id()` | não | lê apenas o setting transacional do tenant |
| `list_current_user_organizations()` | sim | bootstrap antes da seleção de tenant; deriva usuário do setting |
| `list_current_user_accessible_farms(uuid)` | sim | lista fazendas depois de validar membership e escopo |
| `resolve_current_user_tenant_context(uuid, uuid)` | sim | resolve o contexto autorizado sem aceitar userId do cliente |
| `lock_organization_administration(uuid)` | sim | serializa invariantes administrativas do tenant |
| `create_organization_for_current_user(uuid, text)` | sim | criação atômica de organização, membership OWNER e auditoria |
| `accept_organization_invitation(text)` | sim | aceitação atômica por hash, identidade atual e validade |

Todas as funções `SECURITY DEFINER` têm `search_path` fixo, nomes qualificados e execução pública revogada. Somente `app_api` recebe `EXECUTE` quando necessário. O teste `BackendMvpDatabaseReadinessTest` consulta os catálogos do PostgreSQL e falha se uma função elevada perder essas proteções.

## RLS e grants

- RLS é defesa em profundidade; repositories também filtram explicitamente `tenant_id` e `farm_id`.
- `app_api` não tem `SUPERUSER`, `LOGIN`, `BYPASSRLS`, criação de role/database/schema, replicação, `TRUNCATE`, `TRIGGER` ou `REFERENCES`.
- Grants de escrita são específicos por tabela e, quando útil, por coluna. O schema `app` não concede acesso a `PUBLIC`.
- O tenant é instalado com `SET LOCAL ROLE app_api` e `set_config(..., true)` na mesma conexão/transação da operação.

## Índices principais

- Identidade/plataforma: usuário + estado, tenant + estado, escopos por tenant/fazenda, convites por organização/status/expiração e auditoria por organização/data.
- Rebanho: tenant/fazenda/estado/identificação; histórico por animal/data; piquete/ocupação; movimentos e transferências por origem, destino, animal e data.
- Inteligência: pesos, tratamentos e reprodução por tenant/fazenda/animal/data; índices derivados para pendências.
- Planner/relatórios/dashboard: tenant/fazenda/status/data/tipo e índices de leitura temporal adicionados nas Phases 10.
- Inventário: produtos e locais por tenant/fazenda/estado; saldo por tenant/fazenda/local/produto; operação única e razão por data.
- Finanças: categorias por tenant/estado; lançamentos por tenant/fazenda/status, tipo, categoria e vencimento; eventos por lançamento/data.

O teste de prontidão exige ao menos um índice iniciado por `tenant_id` em toda tabela tenant-aware e detecta índices estruturalmente idênticos. A auditoria encontrou e removeu, por migration aditiva, `financial_entries_context_operation_idx`, pois a restrição `UNIQUE (tenant_id, farm_id, operation_id)` já cria o mesmo acesso B-tree.

## Processo verificável

`mvn verify` inicia PostgreSQL 15 limpo com Testcontainers, aplica todas as migrations em ordem e executa contratos, repositories, concorrência, RLS e smoke HTTP. Não há dependência de schema pré-existente nem alteração automática de DDL pela aplicação.
