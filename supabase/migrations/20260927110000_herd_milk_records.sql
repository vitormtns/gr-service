-- Registros imutáveis de produção de leite por animal e fazenda.
alter table app.animal_events drop constraint animal_events_type_check;
alter table app.animal_events add constraint animal_events_type_check check (
  event_type in (
    'CREATED','CORRECTED','SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN',
    'WEIGHED','HEALTH_TREATMENT','HEALTH_TREATMENT_RETRACTED','BREEDING_RECORDED',
    'PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN','MILK_RECORDED'
  )
);
alter table app.animal_events drop constraint animal_events_operation_check;
alter table app.animal_events add constraint animal_events_operation_check check (
  (event_type in (
    'SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN','WEIGHED',
    'HEALTH_TREATMENT','HEALTH_TREATMENT_RETRACTED','BREEDING_RECORDED',
    'PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN','MILK_RECORDED'
  )) = (operation_id is not null)
);

create table app.animal_milk_records (
  id uuid primary key,
  tenant_id uuid not null,
  farm_id uuid not null,
  animal_id uuid not null,
  operation_id uuid not null,
  recorded_on date not null,
  liters numeric(9,3) not null,
  session text,
  notes text,
  actor_user_id uuid,
  recorded_at timestamptz not null default current_timestamp,
  constraint animal_milk_records_farm_fk foreign key (tenant_id,farm_id)
    references app.farms(tenant_id,id) on delete restrict,
  constraint animal_milk_records_animal_fk foreign key (tenant_id,farm_id,animal_id)
    references app.animals(tenant_id,farm_id,id) on delete restrict,
  constraint animal_milk_records_liters_check check (liters > 0),
  constraint animal_milk_records_session_check check
    (session is null or session in ('MORNING','AFTERNOON','EVENING')),
  constraint animal_milk_records_operation_unique unique (tenant_id,farm_id,operation_id)
);
create index animal_milk_records_history_idx on app.animal_milk_records
  (tenant_id,farm_id,animal_id,recorded_on desc,recorded_at desc,id desc);
create index animal_milk_records_period_idx on app.animal_milk_records
  (tenant_id,farm_id,recorded_on,animal_id);
revoke all on app.animal_milk_records from public;
grant select,insert on app.animal_milk_records to app_api;
alter table app.animal_milk_records enable row level security;
alter table app.animal_milk_records force row level security;
create policy animal_milk_records_select on app.animal_milk_records
  for select to app_api using (tenant_id=app.current_tenant_id());
create policy animal_milk_records_insert on app.animal_milk_records
  for insert to app_api with check (tenant_id=app.current_tenant_id());
