-- Phase 06: tenant catalog and farm-scoped stock are deliberately separate.
create table app.inventory_products (
 id uuid primary key, tenant_id uuid not null, name text not null, code text, category text, base_unit text not null, status text not null default 'ACTIVE', version bigint not null default 0, created_at timestamptz not null default current_timestamp, updated_at timestamptz not null default current_timestamp,
 constraint inventory_products_tenant_fk foreign key (tenant_id) references app.organizations(id) on delete restrict,
 constraint inventory_products_unit_check check (base_unit in ('UNIT','KG','G','L','ML','M','M2','M3')),
 constraint inventory_products_status_check check (status in ('ACTIVE','INACTIVE')),
 constraint inventory_products_name_check check (length(btrim(name))>0 and position(chr(0) in name)=0),
 constraint inventory_products_code_check check (code is null or (length(btrim(code))>0 and position(chr(0) in code)=0))
);
create unique index inventory_products_tenant_id_id_uq on app.inventory_products(tenant_id,id);
create unique index inventory_products_tenant_code_uq on app.inventory_products(tenant_id,lower(code)) where code is not null;
create index inventory_products_tenant_status_idx on app.inventory_products(tenant_id,status);
create table app.inventory_locations (
 id uuid primary key, tenant_id uuid not null, farm_id uuid not null, name text not null, code text, status text not null default 'ACTIVE', version bigint not null default 0, created_at timestamptz not null default current_timestamp, updated_at timestamptz not null default current_timestamp,
 constraint inventory_locations_farm_fk foreign key (tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict,
 constraint inventory_locations_name_check check(length(btrim(name))>0 and position(chr(0) in name)=0),
 constraint inventory_locations_code_check check(code is null or (length(btrim(code))>0 and position(chr(0) in code)=0)),
 constraint inventory_locations_status_check check(status in ('ACTIVE','INACTIVE'))
);
create unique index inventory_locations_context_id_uq on app.inventory_locations(tenant_id,farm_id,id);
create unique index inventory_locations_context_code_uq on app.inventory_locations(tenant_id,farm_id,lower(code)) where code is not null;
create index inventory_locations_tenant_farm_status_idx on app.inventory_locations(tenant_id,farm_id,status);
create table app.inventory_balances (
 tenant_id uuid not null, farm_id uuid not null, location_id uuid not null, product_id uuid not null, quantity numeric(19,6) not null default 0, version bigint not null default 0, updated_at timestamptz not null default current_timestamp,
 primary key(tenant_id,farm_id,location_id,product_id),
 constraint inventory_balances_location_fk foreign key(tenant_id,farm_id,location_id) references app.inventory_locations(tenant_id,farm_id,id) on delete restrict,
 constraint inventory_balances_product_fk foreign key(tenant_id,product_id) references app.inventory_products(tenant_id,id) on delete restrict,
 constraint inventory_balances_quantity_check check(quantity>=0)
);
create index inventory_balances_tenant_farm_product_idx on app.inventory_balances(tenant_id,farm_id,product_id);
create table app.inventory_operations (
 tenant_id uuid not null, farm_id uuid not null, operation_id uuid not null, command_payload text not null, primary key(tenant_id,farm_id,operation_id), constraint inventory_operations_farm_fk foreign key(tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict
);
create table app.inventory_movements (
 id uuid primary key, tenant_id uuid not null, farm_id uuid not null, operation_id uuid not null, movement_type text not null, product_id uuid not null, product_name text not null, base_unit text not null, source_location_id uuid, source_location_name text, destination_location_id uuid, destination_location_name text, quantity numeric(19,6) not null, source_balance_after numeric(19,6), destination_balance_after numeric(19,6), occurred_on date not null, actor_user_id uuid, notes text, recorded_at timestamptz not null default current_timestamp,
 constraint inventory_movements_farm_fk foreign key(tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict,
 constraint inventory_movements_product_fk foreign key(tenant_id,product_id) references app.inventory_products(tenant_id,id) on delete restrict,
 constraint inventory_movements_source_fk foreign key(tenant_id,farm_id,source_location_id) references app.inventory_locations(tenant_id,farm_id,id) on delete restrict,
 constraint inventory_movements_destination_fk foreign key(tenant_id,farm_id,destination_location_id) references app.inventory_locations(tenant_id,farm_id,id) on delete restrict,
 constraint inventory_movements_type_check check(movement_type in ('RECEIPT','ISSUE','ADJUSTMENT_IN','ADJUSTMENT_OUT','TRANSFER')),
 constraint inventory_movements_quantity_check check(quantity>0),
 constraint inventory_movements_shape_check check ((movement_type in ('RECEIPT','ADJUSTMENT_IN') and source_location_id is null and destination_location_id is not null) or (movement_type in ('ISSUE','ADJUSTMENT_OUT') and source_location_id is not null and destination_location_id is null) or (movement_type='TRANSFER' and source_location_id is not null and destination_location_id is not null and source_location_id<>destination_location_id)),
 constraint inventory_movements_operation_unique unique(tenant_id,farm_id,operation_id)
);
create index inventory_movements_context_recorded_idx on app.inventory_movements(tenant_id,farm_id,recorded_at desc,id desc);
create index inventory_movements_product_recorded_idx on app.inventory_movements(tenant_id,farm_id,product_id,recorded_at desc,id desc);
create index inventory_movements_source_recorded_idx on app.inventory_movements(tenant_id,farm_id,source_location_id,recorded_at desc,id desc);
create index inventory_movements_destination_recorded_idx on app.inventory_movements(tenant_id,farm_id,destination_location_id,recorded_at desc,id desc);
revoke all on app.inventory_products,app.inventory_locations,app.inventory_balances,app.inventory_operations,app.inventory_movements from public;
grant select,insert,update(name,code,category,status,version,updated_at) on app.inventory_products to app_api;
grant select,insert,update(name,code,status,version,updated_at) on app.inventory_locations to app_api;
grant select,insert,update(quantity,version,updated_at) on app.inventory_balances to app_api;
grant select,insert on app.inventory_operations,app.inventory_movements to app_api;
alter table app.inventory_products enable row level security; alter table app.inventory_products force row level security;
alter table app.inventory_locations enable row level security; alter table app.inventory_locations force row level security;
alter table app.inventory_balances enable row level security; alter table app.inventory_balances force row level security;
alter table app.inventory_operations enable row level security; alter table app.inventory_operations force row level security;
alter table app.inventory_movements enable row level security; alter table app.inventory_movements force row level security;
create policy inventory_products_tenant on app.inventory_products to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
create policy inventory_locations_tenant on app.inventory_locations to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
create policy inventory_balances_tenant on app.inventory_balances to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
create policy inventory_operations_tenant on app.inventory_operations to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
create policy inventory_movements_tenant on app.inventory_movements to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
