-- Histórico de manejo permanece na fazenda de origem quando a custódia do animal muda.
alter table app.animal_weight_measurements drop constraint animal_weight_measurements_animal_fk;
alter table app.animal_weight_measurements add constraint animal_weight_measurements_animal_identity_fk
 foreign key(tenant_id,animal_id) references app.animals(tenant_id,id) on delete restrict;
alter table app.animal_health_treatments drop constraint animal_health_treatments_animal_fk;
alter table app.animal_health_treatments add constraint animal_health_treatments_animal_identity_fk
 foreign key(tenant_id,animal_id) references app.animals(tenant_id,id) on delete restrict;

create table app.herd_planner_items (
 id uuid primary key, tenant_id uuid not null, farm_id uuid not null, animal_id uuid null,
 type text not null, title text not null, notes text null,
 scheduled_for date not null, status text not null default 'OPEN', version bigint not null default 0,
 created_by uuid not null, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), completed_at timestamptz null, cancelled_at timestamptz null,
 constraint herd_planner_items_farm_fk foreign key(tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict,
 constraint herd_planner_items_animal_fk foreign key(tenant_id,animal_id) references app.animals(tenant_id,id) on delete restrict,
 constraint herd_planner_items_type_check check(type in ('GENERAL','WEIGHING','VACCINATION','DEWORMING','BREEDING','PREGNANCY_CHECK','CALVING','MOVEMENT')),
 constraint herd_planner_items_status_check check(status in ('OPEN','COMPLETED','CANCELLED')),
 constraint herd_planner_items_version_check check(version >= 0),
 constraint herd_planner_items_terminal_check check((status='OPEN' and completed_at is null and cancelled_at is null) or (status='COMPLETED' and completed_at is not null and cancelled_at is null) or (status='CANCELLED' and cancelled_at is not null and completed_at is null)),
 constraint herd_planner_items_title_check check(length(btrim(title)) between 1 and 160),
 constraint herd_planner_items_notes_check check(notes is null or (length(notes) <= 2000 and length(btrim(notes)) > 0)),
 constraint herd_planner_items_tenant_farm_id_unique unique(tenant_id,farm_id,id)
);
create table app.herd_planner_operations (
 tenant_id uuid not null, farm_id uuid not null, operation_id uuid not null, operation_type text not null,
 planner_item_id uuid not null, command_payload text not null, resulting_version bigint not null, recorded_at timestamptz not null default now(),
 primary key(tenant_id,farm_id,operation_id),
 constraint herd_planner_operations_farm_fk foreign key(tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict,
 constraint herd_planner_operations_item_fk foreign key(tenant_id,farm_id,planner_item_id) references app.herd_planner_items(tenant_id,farm_id,id) on delete restrict,
 constraint herd_planner_operations_type_check check(operation_type in ('CREATE','CORRECT','COMPLETE','CANCEL')),
 constraint herd_planner_operations_version_check check(resulting_version >= 0)
);
create index herd_planner_items_farm_agenda_idx on app.herd_planner_items(tenant_id,farm_id,status,scheduled_for,created_at,id);
create index herd_planner_items_animal_idx on app.herd_planner_items(tenant_id,farm_id,animal_id,scheduled_for);
revoke all on app.herd_planner_items,app.herd_planner_operations from public;
grant select,insert,update(type,title,notes,scheduled_for,animal_id,status,version,updated_at,completed_at,cancelled_at) on app.herd_planner_items to app_api;
grant select,insert on app.herd_planner_operations to app_api;
alter table app.herd_planner_items enable row level security;
alter table app.herd_planner_items force row level security;
alter table app.herd_planner_operations enable row level security;
alter table app.herd_planner_operations force row level security;
create policy herd_planner_items_tenant on app.herd_planner_items to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
create policy herd_planner_operations_tenant on app.herd_planner_operations to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
