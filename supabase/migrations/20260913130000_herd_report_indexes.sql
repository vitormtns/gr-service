-- Phase 10B: índices dos filtros temporais dos relatórios pecuários.
create index animal_events_report_idx
    on app.animal_events (tenant_id, farm_id, event_type, occurred_on desc, recorded_at desc, id desc);
create index animal_transfers_source_occurred_idx
    on app.animal_transfers (tenant_id, source_farm_id, occurred_on desc, recorded_at desc, id desc);
create index animal_transfers_destination_occurred_idx
    on app.animal_transfers (tenant_id, destination_farm_id, occurred_on desc, recorded_at desc, id desc);
create index animal_weight_measurements_report_idx
    on app.animal_weight_measurements (tenant_id, farm_id, measured_on desc, recorded_at desc, id desc);
create index animal_health_treatments_report_idx
    on app.animal_health_treatments (tenant_id, farm_id, occurred_on desc, recorded_at desc, id desc, treatment_type);
