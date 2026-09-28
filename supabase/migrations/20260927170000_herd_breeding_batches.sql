create table app.herd_breeding_batches (
    tenant_id uuid not null,
    farm_id uuid not null,
    operation_id uuid not null,
    payload_hash text not null,
    mother_ids uuid[] not null,
    pregnancy_ids uuid[] not null,
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, farm_id, operation_id),
    constraint herd_breeding_batches_farm_fk foreign key (tenant_id, farm_id)
        references app.farms (tenant_id, id),
    constraint herd_breeding_batches_hash_check check (length(payload_hash) = 64),
    constraint herd_breeding_batches_size_check check (
        cardinality(mother_ids) between 1 and 100
        and cardinality(mother_ids) = cardinality(pregnancy_ids)
    )
);

revoke all on app.herd_breeding_batches from public;
grant select, insert on app.herd_breeding_batches to app_api;
alter table app.herd_breeding_batches enable row level security;
alter table app.herd_breeding_batches force row level security;
create policy herd_breeding_batches_select on app.herd_breeding_batches
    for select to app_api using (tenant_id = app.current_tenant_id());
create policy herd_breeding_batches_insert on app.herd_breeding_batches
    for insert to app_api with check (tenant_id = app.current_tenant_id());
