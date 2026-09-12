-- Phase 09B: current pregnancy state and tenant-wide maternal lineage.
alter table app.animal_events drop constraint animal_events_type_check;
alter table app.animal_events add constraint animal_events_type_check check (event_type in ('CREATED','CORRECTED','SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN','WEIGHED','HEALTH_TREATMENT','BREEDING_RECORDED','PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN'));
alter table app.animal_events drop constraint animal_events_operation_check;
alter table app.animal_events add constraint animal_events_operation_check check ((event_type in ('SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN','WEIGHED','HEALTH_TREATMENT','BREEDING_RECORDED','PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN')) = (operation_id is not null));
create table app.animal_pregnancies (
 id uuid primary key, tenant_id uuid not null, farm_id uuid not null, mother_animal_id uuid not null,
 service_type text not null, service_on date not null, sire_reference text, expected_calving_on date not null,
 status text not null default 'POSSIBLE', confirmed_on date, ended_on date, termination_reason text, calf_animal_id uuid,
 operation_id uuid not null, command_payload text not null, version bigint not null default 0, created_by uuid, created_at timestamptz not null default current_timestamp, updated_at timestamptz not null default current_timestamp,
 constraint animal_pregnancies_farm_fk foreign key(tenant_id,farm_id) references app.farms(tenant_id,id),
 constraint animal_pregnancies_mother_fk foreign key(tenant_id,mother_animal_id) references app.animals(tenant_id,id),
 constraint animal_pregnancies_calf_fk foreign key(tenant_id,calf_animal_id) references app.animals(tenant_id,id),
 constraint animal_pregnancies_tenant_id_unique unique(tenant_id,id),
 constraint animal_pregnancies_service_type_check check(service_type in ('INSEMINATION','NATURAL_SERVICE')),
 constraint animal_pregnancies_status_check check(status in ('POSSIBLE','CONFIRMED','CALVED','TERMINATED')),
 constraint animal_pregnancies_dates_check check(expected_calving_on > service_on and (confirmed_on is null or confirmed_on >= service_on) and (ended_on is null or ended_on >= service_on)),
 constraint animal_pregnancies_termination_check check((status='TERMINATED')=(ended_on is not null and termination_reason is not null)),
 constraint animal_pregnancies_calf_check check((status='CALVED')=(calf_animal_id is not null))
);
create unique index animal_pregnancies_open_mother_unique on app.animal_pregnancies(tenant_id,mother_animal_id) where status in ('POSSIBLE','CONFIRMED');
create unique index animal_pregnancies_operation_unique on app.animal_pregnancies(tenant_id,farm_id,operation_id);
create index animal_pregnancies_due_idx on app.animal_pregnancies(tenant_id,farm_id,expected_calving_on) where status in ('POSSIBLE','CONFIRMED');
create table app.animal_maternal_relations (
 tenant_id uuid not null, mother_animal_id uuid not null, calf_animal_id uuid not null, pregnancy_id uuid, created_at timestamptz not null default current_timestamp,
 primary key(tenant_id,calf_animal_id), constraint animal_maternal_relations_not_self check(mother_animal_id<>calf_animal_id),
 constraint animal_maternal_relations_mother_fk foreign key(tenant_id,mother_animal_id) references app.animals(tenant_id,id),
 constraint animal_maternal_relations_calf_fk foreign key(tenant_id,calf_animal_id) references app.animals(tenant_id,id),
 constraint animal_maternal_relations_pregnancy_fk foreign key(tenant_id,pregnancy_id) references app.animal_pregnancies(tenant_id,id)
);
create index animal_maternal_relations_mother_idx on app.animal_maternal_relations(tenant_id,mother_animal_id,created_at desc);
revoke all on app.animal_pregnancies,app.animal_maternal_relations from public;
grant select,insert,update on app.animal_pregnancies,app.animal_maternal_relations to app_api;
alter table app.animal_pregnancies enable row level security; alter table app.animal_pregnancies force row level security;
alter table app.animal_maternal_relations enable row level security; alter table app.animal_maternal_relations force row level security;
create policy animal_pregnancies_select on app.animal_pregnancies for select to app_api using(tenant_id=app.current_tenant_id());
create policy animal_pregnancies_insert on app.animal_pregnancies for insert to app_api with check(tenant_id=app.current_tenant_id());
create policy animal_pregnancies_update on app.animal_pregnancies for update to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
create policy animal_maternal_relations_select on app.animal_maternal_relations for select to app_api using(tenant_id=app.current_tenant_id());
create policy animal_maternal_relations_insert on app.animal_maternal_relations for insert to app_api with check(tenant_id=app.current_tenant_id());
