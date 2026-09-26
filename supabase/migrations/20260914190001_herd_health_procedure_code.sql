alter table app.animal_health_treatments add column procedure_code text;
alter table app.animal_health_treatments add constraint animal_health_treatments_procedure_code_check
 check(procedure_code is null or procedure_code in ('BRUCELLOSIS'));
