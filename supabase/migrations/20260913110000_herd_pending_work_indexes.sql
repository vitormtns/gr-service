-- Phase 09C: indexes for the set-based, derived pending-work read model.
create index animal_weight_measurements_pending_latest_idx on app.animal_weight_measurements(tenant_id,animal_id,measured_on desc,recorded_at desc,id desc);
create index animal_health_treatments_pending_latest_idx on app.animal_health_treatments(tenant_id,animal_id,treatment_type,occurred_on desc,recorded_at desc,id desc);
create index animal_pregnancies_pending_open_idx on app.animal_pregnancies(tenant_id,mother_animal_id,expected_calving_on) where status in ('POSSIBLE','CONFIRMED');
