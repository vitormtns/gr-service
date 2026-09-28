-- Grupos de manejo independentes da localização física dos animais.
create table app.herd_groups (
  id uuid primary key,
  tenant_id uuid not null,
  farm_id uuid not null,
  name text not null,
  kind text not null,
  status text not null default 'ACTIVE',
  sex text,
  animal_status text,
  min_age_months integer,
  max_age_months integer,
  only_reproduction_active boolean not null default false,
  only_missing_profile boolean not null default false,
  version bigint not null default 0,
  created_at timestamptz not null default current_timestamp,
  updated_at timestamptz not null default current_timestamp,
  constraint herd_groups_farm_fk foreign key (tenant_id,farm_id) references app.farms(tenant_id,id),
  constraint herd_groups_tenant_farm_id_unique unique (tenant_id,farm_id,id),
  constraint herd_groups_name_check check (name <> '' and char_length(name) <= 120),
  constraint herd_groups_kind_check check (kind in ('MANUAL','SMART')),
  constraint herd_groups_status_check check (status in ('ACTIVE','ARCHIVED')),
  constraint herd_groups_sex_check check (sex is null or sex in ('MALE','FEMALE')),
  constraint herd_groups_animal_status_check check (animal_status is null or animal_status in ('ACTIVE','SOLD','DECEASED','TRANSFERRED','ARCHIVED')),
  constraint herd_groups_age_check check (min_age_months is null or min_age_months >= 0),
  constraint herd_groups_max_age_check check (max_age_months is null or max_age_months >= 0),
  constraint herd_groups_age_order_check check (min_age_months is null or max_age_months is null or min_age_months <= max_age_months),
  constraint herd_groups_manual_rules_check check (kind='SMART' or (sex is null and animal_status is null and min_age_months is null and max_age_months is null and not only_reproduction_active and not only_missing_profile)),
  constraint herd_groups_version_check check (version >= 0)
);
create unique index herd_groups_active_name_unique on app.herd_groups
  (tenant_id,farm_id,lower(name)) where status='ACTIVE';
create index herd_groups_list_idx on app.herd_groups(tenant_id,farm_id,status,name,id);
revoke all on app.herd_groups from public;
grant select,insert on app.herd_groups to app_api;
grant update (name,status,sex,animal_status,min_age_months,max_age_months,only_reproduction_active,only_missing_profile,version,updated_at) on app.herd_groups to app_api;
alter table app.herd_groups enable row level security;
alter table app.herd_groups force row level security;
create policy herd_groups_select on app.herd_groups for select to app_api using (tenant_id=app.current_tenant_id());
create policy herd_groups_insert on app.herd_groups for insert to app_api with check (tenant_id=app.current_tenant_id());
create policy herd_groups_update on app.herd_groups for update to app_api using (tenant_id=app.current_tenant_id()) with check (tenant_id=app.current_tenant_id());

create table app.herd_group_members (
  tenant_id uuid not null,
  farm_id uuid not null,
  group_id uuid not null,
  animal_id uuid not null,
  created_at timestamptz not null default current_timestamp,
  primary key (tenant_id,farm_id,group_id,animal_id),
  constraint herd_group_members_group_fk foreign key (tenant_id,farm_id,group_id) references app.herd_groups(tenant_id,farm_id,id),
  constraint herd_group_members_animal_fk foreign key (tenant_id,farm_id,animal_id) references app.animals(tenant_id,farm_id,id)
);
create index herd_group_members_animal_idx on app.herd_group_members(tenant_id,farm_id,animal_id,group_id);
revoke all on app.herd_group_members from public;
grant select,insert,delete on app.herd_group_members to app_api;
alter table app.herd_group_members enable row level security;
alter table app.herd_group_members force row level security;
create policy herd_group_members_select on app.herd_group_members for select to app_api using (tenant_id=app.current_tenant_id());
create policy herd_group_members_insert on app.herd_group_members for insert to app_api with check (tenant_id=app.current_tenant_id());
create policy herd_group_members_delete on app.herd_group_members for delete to app_api using (tenant_id=app.current_tenant_id());
