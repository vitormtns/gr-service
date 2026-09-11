-- PostgreSQL text cannot contain NUL. Calling chr(0), however, raises before the
-- CHECK can evaluate, so retain the useful non-blank constraint without chr(0).
alter table app.inventory_products drop constraint inventory_products_name_check;
alter table app.inventory_products drop constraint inventory_products_code_check;
alter table app.inventory_locations drop constraint inventory_locations_name_check;
alter table app.inventory_locations drop constraint inventory_locations_code_check;

alter table app.inventory_products add constraint inventory_products_name_check check(length(btrim(name))>0);
alter table app.inventory_products add constraint inventory_products_code_check check(code is null or length(btrim(code))>0);
alter table app.inventory_locations add constraint inventory_locations_name_check check(length(btrim(name))>0);
alter table app.inventory_locations add constraint inventory_locations_code_check check(code is null or length(btrim(code))>0);

-- The idempotency read uses SELECT FOR UPDATE after taking a transaction-scoped
-- advisory lock. PostgreSQL requires UPDATE privilege for that lock mode.
grant update(command_payload) on app.inventory_operations to app_api;
