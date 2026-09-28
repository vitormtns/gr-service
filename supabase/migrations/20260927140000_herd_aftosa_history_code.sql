-- Classificação histórica de vacinação; nenhum prazo ou obrigação é inferido.
alter table app.animal_health_treatments
  drop constraint animal_health_treatments_procedure_code_check;
alter table app.animal_health_treatments
  add constraint animal_health_treatments_procedure_code_check
  check (procedure_code is null or procedure_code in ('BRUCELLOSIS','FOOT_AND_MOUTH_DISEASE'));
