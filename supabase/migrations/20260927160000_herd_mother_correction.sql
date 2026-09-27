-- Correção auditada de vínculo materno não originado de uma gestação registrada.
alter table app.animal_events drop constraint animal_events_type_check;
alter table app.animal_events add constraint animal_events_type_check check (
    event_type in (
        'CREATED','CORRECTED','SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN',
        'WEIGHED','HEALTH_TREATMENT','HEALTH_TREATMENT_RETRACTED','BREEDING_RECORDED',
        'PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN','MILK_RECORDED',
        'MOTHER_CORRECTED'
    )
);
alter table app.animal_events drop constraint animal_events_operation_check;
alter table app.animal_events add constraint animal_events_operation_check check (
    (event_type in (
        'SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN','WEIGHED',
        'HEALTH_TREATMENT','HEALTH_TREATMENT_RETRACTED','BREEDING_RECORDED',
        'PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN','MILK_RECORDED',
        'MOTHER_CORRECTED'
    )) = (operation_id is not null)
);

grant update (mother_animal_id), delete on app.animal_maternal_relations to app_api;
create policy animal_maternal_relations_update on app.animal_maternal_relations
    for update to app_api
    using (tenant_id = app.current_tenant_id() and pregnancy_id is null)
    with check (tenant_id = app.current_tenant_id() and pregnancy_id is null);
create policy animal_maternal_relations_delete on app.animal_maternal_relations
    for delete to app_api
    using (tenant_id = app.current_tenant_id() and pregnancy_id is null);
