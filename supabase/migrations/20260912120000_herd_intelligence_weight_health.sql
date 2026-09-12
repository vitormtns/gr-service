-- Phase 09A: immutable herd-management facts and replay records.
alter table app.animal_events drop constraint animal_events_type_check;
alter table app.animal_events add constraint animal_events_type_check check (event_type in ('CREATED','CORRECTED','SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN','WEIGHED','HEALTH_TREATMENT'));
alter table app.animal_events drop constraint animal_events_operation_check;
alter table app.animal_events add constraint animal_events_operation_check check ((event_type in ('SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN','WEIGHED','HEALTH_TREATMENT')) = (operation_id is not null));

create table app.herd_management_operations (
 tenant_id uuid not null, farm_id uuid not null, operation_id uuid not null, command_type text not null, command_payload text not null, created_at timestamptz not null default current_timestamp,
 primary key (tenant_id,farm_id,operation_id),
 constraint herd_management_operations_farm_fk foreign key (tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict,
 constraint herd_management_operations_type_check check(command_type in ('WEIGHT','HEALTH'))
);
create table app.animal_weight_measurements (
 id uuid primary key, tenant_id uuid not null, farm_id uuid not null, animal_id uuid not null, operation_id uuid not null, measured_on date not null, weight_kg numeric(8,3) not null, notes text, actor_user_id uuid, recorded_at timestamptz not null default current_timestamp,
 constraint animal_weight_measurements_farm_fk foreign key(tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict,
 constraint animal_weight_measurements_animal_fk foreign key(tenant_id,farm_id,animal_id) references app.animals(tenant_id,farm_id,id) on delete restrict,
 constraint animal_weight_measurements_weight_check check(weight_kg>0),
 constraint animal_weight_measurements_operation_animal_unique unique(tenant_id,farm_id,operation_id,animal_id)
);
create index animal_weight_measurements_history_idx on app.animal_weight_measurements(tenant_id,farm_id,animal_id,measured_on desc,recorded_at desc,id desc);
create table app.animal_health_treatments (
 id uuid primary key, tenant_id uuid not null, farm_id uuid not null, animal_id uuid not null, operation_id uuid not null, treatment_type text not null, occurred_on date not null, product text, protocol text, next_due_on date, notes text, actor_user_id uuid, recorded_at timestamptz not null default current_timestamp,
 constraint animal_health_treatments_farm_fk foreign key(tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict,
 constraint animal_health_treatments_animal_fk foreign key(tenant_id,farm_id,animal_id) references app.animals(tenant_id,farm_id,id) on delete restrict,
 constraint animal_health_treatments_type_check check(treatment_type in ('VACCINATION','DEWORMING')),
 constraint animal_health_treatments_due_check check(next_due_on is null or next_due_on>=occurred_on),
 constraint animal_health_treatments_operation_animal_unique unique(tenant_id,farm_id,operation_id,animal_id)
);
create index animal_health_treatments_history_idx on app.animal_health_treatments(tenant_id,farm_id,animal_id,occurred_on desc,recorded_at desc,id desc);
create index animal_health_treatments_due_idx on app.animal_health_treatments(tenant_id,farm_id,treatment_type,next_due_on) where next_due_on is not null;
revoke all on app.herd_management_operations,app.animal_weight_measurements,app.animal_health_treatments from public;
grant select,insert on app.herd_management_operations,app.animal_weight_measurements,app.animal_health_treatments to app_api;
alter table app.herd_management_operations enable row level security; alter table app.herd_management_operations force row level security;
alter table app.animal_weight_measurements enable row level security; alter table app.animal_weight_measurements force row level security;
alter table app.animal_health_treatments enable row level security; alter table app.animal_health_treatments force row level security;
create policy herd_management_operations_select on app.herd_management_operations for select to app_api using(tenant_id=app.current_tenant_id());
create policy herd_management_operations_insert on app.herd_management_operations for insert to app_api with check(tenant_id=app.current_tenant_id());
create policy animal_weight_measurements_select on app.animal_weight_measurements for select to app_api using(tenant_id=app.current_tenant_id());
create policy animal_weight_measurements_insert on app.animal_weight_measurements for insert to app_api with check(tenant_id=app.current_tenant_id());
create policy animal_health_treatments_select on app.animal_health_treatments for select to app_api using(tenant_id=app.current_tenant_id());
create policy animal_health_treatments_insert on app.animal_health_treatments for insert to app_api with check(tenant_id=app.current_tenant_id());
