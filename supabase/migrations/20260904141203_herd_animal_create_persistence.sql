grant insert on app.animals to app_api;

create policy animals_tenant_insert_isolation on app.animals
for insert
to app_api
with check (tenant_id = app.current_tenant_id());
