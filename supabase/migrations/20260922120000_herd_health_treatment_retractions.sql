-- Phase 09B.16: append-only retraction facts for health treatments.
-- Storage foundation only. No reader consults this table yet, and no
-- production operation creates retractions. The original treatment row
-- is never modified by retraction.
create table app.animal_health_treatment_retractions (
  id uuid primary key,
  tenant_id uuid not null,
  farm_id uuid not null,
  animal_id uuid not null,
  treatment_id uuid not null,
  operation_id uuid not null,
  reason text,
  actor_user_id uuid,
  retracted_at timestamptz not null default current_timestamp,
  constraint animal_health_treatment_retractions_farm_fk foreign key (tenant_id, farm_id)
    references app.farms (tenant_id, id) on delete restrict,
  constraint animal_health_treatment_retractions_animal_fk foreign key (tenant_id, farm_id, animal_id)
    references app.animals (tenant_id, farm_id, id) on delete restrict,
  constraint animal_health_treatment_retractions_treatment_fk foreign key (treatment_id)
    references app.animal_health_treatments (id) on delete restrict,
  constraint animal_health_treatment_retractions_treatment_unique unique (treatment_id),
  constraint animal_health_treatment_retractions_operation_unique unique (tenant_id, farm_id, operation_id)
);
revoke all on app.animal_health_treatment_retractions from public;
grant select, insert on app.animal_health_treatment_retractions to app_api;
alter table app.animal_health_treatment_retractions enable row level security;
alter table app.animal_health_treatment_retractions force row level security;
create policy animal_health_treatment_retractions_select on app.animal_health_treatment_retractions
  for select to app_api using (tenant_id = app.current_tenant_id());
create policy animal_health_treatment_retractions_insert on app.animal_health_treatment_retractions
  for insert to app_api with check (
    tenant_id = app.current_tenant_id()
    and exists (
      select 1 from app.animal_health_treatments target
      where target.id = animal_health_treatment_retractions.treatment_id
        and target.tenant_id = animal_health_treatment_retractions.tenant_id
        and target.farm_id = animal_health_treatment_retractions.farm_id
        and target.animal_id = animal_health_treatment_retractions.animal_id
    )
  );
