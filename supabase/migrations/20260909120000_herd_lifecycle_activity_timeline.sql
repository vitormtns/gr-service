grant update (status, version, updated_at) on app.animals to app_api;
alter table app.animals add constraint animals_tenant_farm_id_unique unique (tenant_id, farm_id, id);

create table app.animal_events (
    id uuid primary key,
    tenant_id uuid not null,
    farm_id uuid not null,
    animal_id uuid not null,
    event_type text not null,
    operation_id uuid,
    actor_user_id uuid,
    occurred_on date,
    recorded_at timestamptz not null default current_timestamp,
    resulting_version bigint not null,
    payload jsonb not null default '{}'::jsonb,
    constraint animal_events_farm_fk foreign key (tenant_id, farm_id) references app.farms (tenant_id, id) on delete restrict,
    constraint animal_events_animal_fk foreign key (tenant_id, farm_id, animal_id) references app.animals (tenant_id, farm_id, id) on delete restrict,
    constraint animal_events_type_check check (event_type in ('CREATED', 'CORRECTED', 'SOLD', 'DECEASED')),
    constraint animal_events_version_check check (resulting_version >= 0),
    constraint animal_events_lifecycle_operation_check check ((event_type in ('SOLD','DECEASED')) = (operation_id is not null))
);
create unique index animal_events_tenant_farm_operation_unique on app.animal_events (tenant_id, farm_id, operation_id) where operation_id is not null;
create index animal_events_history_idx on app.animal_events (tenant_id, farm_id, animal_id, recorded_at desc, id desc);
revoke all on app.animal_events from public;
grant select, insert on app.animal_events to app_api;
alter table app.animal_events enable row level security;
alter table app.animal_events force row level security;
create policy animal_events_tenant_select on app.animal_events for select to app_api using (tenant_id = app.current_tenant_id());
create policy animal_events_tenant_insert on app.animal_events for insert to app_api with check (tenant_id = app.current_tenant_id());

insert into app.animal_events (id, tenant_id, farm_id, animal_id, event_type, actor_user_id, recorded_at, resulting_version, payload)
select gen_random_uuid(), a.tenant_id, a.farm_id, a.id, 'CREATED', null, a.created_at, 0,
 jsonb_strip_nulls(jsonb_build_object('identification',a.identification,'name',a.name,'sex',a.sex,'birthDate',a.birth_date))
from app.animals a
where not exists (select 1 from app.animal_events e where e.animal_id=a.id and e.event_type='CREATED');
