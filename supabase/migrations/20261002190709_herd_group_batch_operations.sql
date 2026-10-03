-- Recibos append-only da criação/associação de seleção explícita a grupos.
create table app.herd_group_operations (
  tenant_id uuid not null,
  farm_id uuid not null,
  operation_id uuid not null,
  group_id uuid not null,
  command_payload text not null,
  result_payload text not null,
  recorded_at timestamptz not null default current_timestamp,
  primary key (tenant_id, farm_id, operation_id),
  foreign key (tenant_id, farm_id, group_id) references app.herd_groups(tenant_id, farm_id, id)
);
revoke all on app.herd_group_operations from public;
grant select, insert on app.herd_group_operations to app_api;
alter table app.herd_group_operations enable row level security;
alter table app.herd_group_operations force row level security;
create policy herd_group_operations_select on app.herd_group_operations for select to app_api
  using (tenant_id = app.current_tenant_id());
create policy herd_group_operations_insert on app.herd_group_operations for insert to app_api
  with check (tenant_id = app.current_tenant_id());
