-- Roles are cluster-wide and can survive a local database reset, so role creation
-- must handle that lifecycle while restoring the required security attributes.
do $$
begin
    if not exists (select 1 from pg_catalog.pg_roles where rolname = 'app_api') then
        create role app_api
            nologin
            nosuperuser
            nocreatedb
            nocreaterole
            noinherit
            noreplication
            nobypassrls;
    else
        alter role app_api
            nologin
            nosuperuser
            nocreatedb
            nocreaterole
            noinherit
            noreplication
            nobypassrls;
    end if;
end
$$;

create schema app;

revoke all on schema app from public;
grant usage on schema app to app_api;

create function app.current_tenant_id()
returns uuid
language sql
stable
parallel safe
set search_path = ''
as $$
    select nullif(pg_catalog.current_setting('app.current_tenant_id', true), '')::uuid
$$;

revoke all on function app.current_tenant_id() from public;
grant execute on function app.current_tenant_id() to app_api;

create table app.users (
    id uuid primary key,
    email text,
    display_name text,
    status text not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    version bigint not null default 0,
    constraint users_status_check
        check (status in ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    constraint users_version_check
        check (version >= 0)
);

create table app.organizations (
    id uuid primary key,
    name text not null,
    status text not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    version bigint not null default 0,
    constraint organizations_name_not_blank_check
        check (length(btrim(name)) > 0),
    constraint organizations_status_check
        check (status in ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    constraint organizations_version_check
        check (version >= 0)
);

create table app.farms (
    id uuid primary key,
    tenant_id uuid not null,
    name text not null,
    status text not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    version bigint not null default 0,
    constraint farms_tenant_fk
        foreign key (tenant_id) references app.organizations (id) on delete restrict,
    constraint farms_name_not_blank_check
        check (length(btrim(name)) > 0),
    constraint farms_status_check
        check (status in ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    constraint farms_version_check
        check (version >= 0),
    constraint farms_tenant_id_unique
        unique (tenant_id, id)
);

create table app.organization_memberships (
    id uuid primary key,
    tenant_id uuid not null,
    user_id uuid not null,
    role_key text not null,
    status text not null,
    farm_scope_mode text not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    version bigint not null default 0,
    constraint organization_memberships_tenant_fk
        foreign key (tenant_id) references app.organizations (id) on delete restrict,
    constraint organization_memberships_user_fk
        foreign key (user_id) references app.users (id) on delete restrict,
    constraint organization_memberships_role_key_check
        check (role_key in ('OWNER', 'ADMIN', 'MANAGER', 'OPERATOR', 'VIEWER')),
    constraint organization_memberships_status_check
        check (status in ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    constraint organization_memberships_farm_scope_mode_check
        check (farm_scope_mode in ('ALL_FARMS', 'SELECTED_FARMS')),
    constraint organization_memberships_version_check
        check (version >= 0),
    constraint organization_memberships_tenant_user_unique
        unique (tenant_id, user_id),
    constraint organization_memberships_tenant_id_unique
        unique (tenant_id, id)
);

create table app.membership_farm_scopes (
    tenant_id uuid not null,
    membership_id uuid not null,
    farm_id uuid not null,
    created_at timestamptz not null default current_timestamp,
    constraint membership_farm_scopes_pk
        primary key (tenant_id, membership_id, farm_id),
    constraint membership_farm_scopes_membership_fk
        foreign key (tenant_id, membership_id)
        references app.organization_memberships (tenant_id, id)
        on delete restrict,
    constraint membership_farm_scopes_farm_fk
        foreign key (tenant_id, farm_id)
        references app.farms (tenant_id, id)
        on delete restrict
);

create index farms_tenant_status_idx
    on app.farms (tenant_id, status);

create index organization_memberships_tenant_status_idx
    on app.organization_memberships (tenant_id, status);

create index organization_memberships_user_status_idx
    on app.organization_memberships (user_id, status);

create index membership_farm_scopes_tenant_farm_idx
    on app.membership_farm_scopes (tenant_id, farm_id);

revoke all on all tables in schema app from public;

grant select, insert, update, delete on table
    app.users,
    app.organizations,
    app.farms,
    app.organization_memberships,
    app.membership_farm_scopes
to app_api;

alter table app.organizations enable row level security;
alter table app.organizations force row level security;
alter table app.farms enable row level security;
alter table app.farms force row level security;
alter table app.organization_memberships enable row level security;
alter table app.organization_memberships force row level security;
alter table app.membership_farm_scopes enable row level security;
alter table app.membership_farm_scopes force row level security;

create policy organizations_tenant_isolation
on app.organizations
for all
to app_api
using (id = app.current_tenant_id())
with check (id = app.current_tenant_id());

create policy farms_tenant_isolation
on app.farms
for all
to app_api
using (tenant_id = app.current_tenant_id())
with check (tenant_id = app.current_tenant_id());

create policy organization_memberships_tenant_isolation
on app.organization_memberships
for all
to app_api
using (tenant_id = app.current_tenant_id())
with check (tenant_id = app.current_tenant_id());

create policy membership_farm_scopes_tenant_isolation
on app.membership_farm_scopes
for all
to app_api
using (tenant_id = app.current_tenant_id())
with check (tenant_id = app.current_tenant_id());
