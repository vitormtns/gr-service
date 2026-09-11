-- Phase 05E: territorial unit, current animal location and append-only movement projection.
create table app.paddocks (
    id uuid primary key,
    tenant_id uuid not null,
    farm_id uuid not null,
    name text not null,
    code text,
    status text not null default 'ACTIVE',
    version bigint not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint paddocks_farm_fk foreign key (tenant_id, farm_id) references app.farms (tenant_id, id) on delete restrict,
    constraint paddocks_name_valid check (name <> '' and char_length(name) <= 120),
    constraint paddocks_code_valid check (code is null or (code <> '' and char_length(code) <= 60)),
    constraint paddocks_status_check check (status in ('ACTIVE', 'INACTIVE')),
    constraint paddocks_version_check check (version >= 0),
    constraint paddocks_tenant_farm_id_unique unique (tenant_id, farm_id, id)
);
create unique index paddocks_tenant_farm_name_unique on app.paddocks (tenant_id, farm_id, lower(regexp_replace(name, '(^[[:space:]]+|[[:space:]]+$)', '', 'g')));
create unique index paddocks_tenant_farm_code_unique on app.paddocks (tenant_id, farm_id, lower(regexp_replace(code, '(^[[:space:]]+|[[:space:]]+$)', '', 'g'))) where code is not null;
create index paddocks_tenant_farm_status_name_idx on app.paddocks (tenant_id, farm_id, status, name, id);
revoke all on app.paddocks from public;
grant select, insert on app.paddocks to app_api;
grant update (name, code, status, version, updated_at) on app.paddocks to app_api;
alter table app.paddocks enable row level security;
alter table app.paddocks force row level security;
create policy paddocks_tenant_select on app.paddocks for select to app_api using (tenant_id = app.current_tenant_id());
create policy paddocks_tenant_insert on app.paddocks for insert to app_api with check (tenant_id = app.current_tenant_id());
create policy paddocks_tenant_update on app.paddocks for update to app_api using (tenant_id = app.current_tenant_id()) with check (tenant_id = app.current_tenant_id());

alter table app.animals add column paddock_id uuid;
alter table app.animals add constraint animals_paddock_same_farm_fk foreign key (tenant_id, farm_id, paddock_id) references app.paddocks (tenant_id, farm_id, id) on delete restrict;
grant update (paddock_id, version, updated_at) on app.animals to app_api;
create index animals_tenant_farm_paddock_status_idx on app.animals (tenant_id, farm_id, paddock_id, status, id);

alter table app.animal_events drop constraint animal_events_lifecycle_operation_check;
alter table app.animal_events drop constraint animal_events_type_check;
alter table app.animal_events add constraint animal_events_type_check check (event_type in ('CREATED', 'CORRECTED', 'SOLD', 'DECEASED', 'MOVED'));
alter table app.animal_events add constraint animal_events_operation_check check ((event_type in ('SOLD','DECEASED','MOVED')) = (operation_id is not null));
drop index app.animal_events_tenant_farm_operation_unique;
create unique index animal_events_tenant_farm_operation_animal_unique on app.animal_events (tenant_id, farm_id, operation_id, animal_id) where operation_id is not null;

-- A compact operation record keeps batch replay semantics independent of event ordering.
create table app.herd_movement_operations (
    tenant_id uuid not null,
    farm_id uuid not null,
    operation_id uuid not null,
    command_payload text not null,
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, farm_id, operation_id),
    constraint herd_movement_operations_farm_fk foreign key (tenant_id, farm_id) references app.farms (tenant_id, id) on delete restrict
);
revoke all on app.herd_movement_operations from public;
grant select, insert on app.herd_movement_operations to app_api;
alter table app.herd_movement_operations enable row level security;
alter table app.herd_movement_operations force row level security;
create policy herd_movement_operations_tenant_select on app.herd_movement_operations for select to app_api using (tenant_id = app.current_tenant_id());
create policy herd_movement_operations_tenant_insert on app.herd_movement_operations for insert to app_api with check (tenant_id = app.current_tenant_id());

-- Structured projection avoids JSONB scans for paddock operational logs.
create table app.herd_movements (
    event_id uuid primary key references app.animal_events(id) on delete restrict,
    tenant_id uuid not null,
    farm_id uuid not null,
    animal_id uuid not null,
    operation_id uuid not null,
    source_paddock_id uuid,
    destination_paddock_id uuid not null,
    constraint herd_movements_farm_fk foreign key (tenant_id, farm_id) references app.farms (tenant_id, id) on delete restrict,
    constraint herd_movements_animal_fk foreign key (tenant_id, farm_id, animal_id) references app.animals (tenant_id, farm_id, id) on delete restrict,
    constraint herd_movements_source_fk foreign key (tenant_id, farm_id, source_paddock_id) references app.paddocks (tenant_id, farm_id, id) on delete restrict,
    constraint herd_movements_destination_fk foreign key (tenant_id, farm_id, destination_paddock_id) references app.paddocks (tenant_id, farm_id, id) on delete restrict
);
revoke all on app.herd_movements from public;
grant select, insert on app.herd_movements to app_api;
alter table app.herd_movements enable row level security;
alter table app.herd_movements force row level security;
create policy herd_movements_tenant_select on app.herd_movements for select to app_api using (tenant_id = app.current_tenant_id());
create policy herd_movements_tenant_insert on app.herd_movements for insert to app_api with check (tenant_id = app.current_tenant_id());
create index herd_movements_source_idx on app.herd_movements (tenant_id, farm_id, source_paddock_id, event_id);
create index herd_movements_destination_idx on app.herd_movements (tenant_id, farm_id, destination_paddock_id, event_id);
