create table app.herd_animal_imports (
    tenant_id uuid not null,
    farm_id uuid not null,
    operation_id uuid not null,
    payload_hash text not null,
    animal_ids uuid[] not null,
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, farm_id, operation_id),
    constraint herd_animal_imports_farm_fk foreign key (tenant_id, farm_id)
        references app.farms (tenant_id, id),
    constraint herd_animal_imports_hash_check check (length(payload_hash) = 64),
    constraint herd_animal_imports_ids_check check (cardinality(animal_ids) between 1 and 100)
);

revoke all on app.herd_animal_imports from public;
grant select, insert on app.herd_animal_imports to app_api;
alter table app.herd_animal_imports enable row level security;
alter table app.herd_animal_imports force row level security;
create policy herd_animal_imports_select on app.herd_animal_imports
    for select to app_api using (tenant_id = app.current_tenant_id());
create policy herd_animal_imports_insert on app.herd_animal_imports
    for insert to app_api with check (tenant_id = app.current_tenant_id());
