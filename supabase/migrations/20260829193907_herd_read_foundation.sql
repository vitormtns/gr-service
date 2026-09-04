create table app.animals (
    id uuid primary key,
    tenant_id uuid not null,
    farm_id uuid not null,
    identification text not null,
    name text,
    sex text not null,
    birth_date date,
    status text not null default 'ACTIVE',
    version bigint not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint animals_tenant_farm_fk foreign key (tenant_id, farm_id)
        references app.farms (tenant_id, id) on delete restrict,
    constraint animals_identification_not_blank_check check (
        length(identification) <= 100
        and identification !~ '^[[:space:]]*$'
    ),
    constraint animals_name_check check (
        name is null or (
            length(name) <= 255
            and name !~ '^[[:space:]]*$'
        )
    ),
    constraint animals_sex_check check (sex in ('MALE', 'FEMALE')),
    constraint animals_status_check check (status in ('ACTIVE', 'SOLD', 'DECEASED', 'TRANSFERRED', 'ARCHIVED')),
    constraint animals_version_check check (version >= 0)
);

create unique index animals_tenant_farm_identification_unique
    on app.animals (
        tenant_id,
        farm_id,
        lower(regexp_replace(identification, '(^[[:space:]]+|[[:space:]]+$)', '', 'g'))
    );
create index animals_tenant_farm_status_identification_idx
    on app.animals (tenant_id, farm_id, status, identification, id);

revoke all on app.animals from public;
grant select on app.animals to app_api;
alter table app.animals enable row level security;
alter table app.animals force row level security;
create policy animals_tenant_isolation on app.animals for select to app_api
    using (tenant_id = app.current_tenant_id());
