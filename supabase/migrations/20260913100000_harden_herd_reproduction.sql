-- Phase 09B hardening: maternal relations are append-only for the runtime role.
revoke update on app.animal_maternal_relations from app_api;

alter table app.animal_pregnancies
    add constraint animal_pregnancies_state_shape_check check (
        (status = 'POSSIBLE'
            and confirmed_on is null and ended_on is null
            and termination_reason is null and calf_animal_id is null)
        or (status = 'CONFIRMED'
            and confirmed_on is not null and ended_on is null
            and termination_reason is null and calf_animal_id is null)
        or (status = 'TERMINATED'
            and ended_on is not null and termination_reason is not null
            and calf_animal_id is null)
        or (status = 'CALVED'
            and ended_on is not null and termination_reason is null
            and calf_animal_id is not null)
    );
