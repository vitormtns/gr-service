grant update (identification, name, sex, birth_date, version, updated_at) on app.animals to app_api;

create policy animals_tenant_update_isolation on app.animals
for update
to app_api
using (tenant_id = app.current_tenant_id())
with check (tenant_id = app.current_tenant_id());
