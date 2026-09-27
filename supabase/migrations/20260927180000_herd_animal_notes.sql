alter table app.animal_events drop constraint animal_events_type_check;
alter table app.animal_events add constraint animal_events_type_check check (
    event_type in (
        'CREATED','CORRECTED','SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN',
        'WEIGHED','HEALTH_TREATMENT','HEALTH_TREATMENT_RETRACTED','BREEDING_RECORDED',
        'PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN','MILK_RECORDED',
        'MOTHER_CORRECTED','NOTE_RECORDED'
    )
);
alter table app.animal_events drop constraint animal_events_operation_check;
alter table app.animal_events add constraint animal_events_operation_check check (
    (event_type in (
        'SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN','WEIGHED',
        'HEALTH_TREATMENT','HEALTH_TREATMENT_RETRACTED','BREEDING_RECORDED',
        'PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN','MILK_RECORDED',
        'MOTHER_CORRECTED','NOTE_RECORDED'
    )) = (operation_id is not null)
);
