-- Phase 07: tenant-wide category catalogue and farm-scoped financial ledger.
create table app.financial_categories (
 id uuid primary key, tenant_id uuid not null references app.organizations(id) on delete restrict,
 name text not null, code text, kind text not null, status text not null default 'ACTIVE', version bigint not null default 0,
 created_at timestamptz not null default current_timestamp, updated_at timestamptz not null default current_timestamp,
 constraint financial_categories_name_check check(length(btrim(name)) between 1 and 120 and position(chr(0) in name)=0),
 constraint financial_categories_code_check check(code is null or (length(btrim(code)) between 1 and 60 and position(chr(0) in code)=0)),
 constraint financial_categories_kind_check check(kind in ('INCOME','EXPENSE','BOTH')),
 constraint financial_categories_status_check check(status in ('ACTIVE','INACTIVE'))
);
create unique index financial_categories_tenant_id_id_uq on app.financial_categories(tenant_id,id);
create unique index financial_categories_tenant_code_uq on app.financial_categories(tenant_id,lower(code)) where code is not null;
create index financial_categories_tenant_status_code_idx on app.financial_categories(tenant_id,status,code);
create table app.financial_entries (
 id uuid primary key, tenant_id uuid not null, farm_id uuid not null, operation_id uuid not null,
 type text not null, status text not null default 'PENDING', category_id uuid not null, category_name_snapshot text not null,
 description text not null, amount numeric(19,2) not null, due_on date not null, settled_on date, cancelled_at timestamptz, notes text,
 version bigint not null default 0, created_by_user_id uuid, updated_by_user_id uuid,
 created_at timestamptz not null default current_timestamp, updated_at timestamptz not null default current_timestamp,
 constraint financial_entries_farm_fk foreign key(tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict,
 constraint financial_entries_category_fk foreign key(tenant_id,category_id) references app.financial_categories(tenant_id,id) on delete restrict,
 constraint financial_entries_type_check check(type in ('INCOME','EXPENSE')),
 constraint financial_entries_status_check check(status in ('PENDING','SETTLED','CANCELLED')),
 constraint financial_entries_amount_check check(amount>0),
 constraint financial_entries_description_check check(length(btrim(description)) between 1 and 240 and position(chr(0) in description)=0),
 constraint financial_entries_lifecycle_check check((status='PENDING' and settled_on is null and cancelled_at is null) or (status='SETTLED' and settled_on is not null and cancelled_at is null) or (status='CANCELLED' and settled_on is null and cancelled_at is not null)),
 unique(tenant_id,farm_id,operation_id)
);
create unique index financial_entries_context_id_uq on app.financial_entries(tenant_id,farm_id,id);
create index financial_entries_context_status_due_idx on app.financial_entries(tenant_id,farm_id,status,due_on);
create index financial_entries_context_type_due_idx on app.financial_entries(tenant_id,farm_id,type,due_on);
create index financial_entries_context_category_due_idx on app.financial_entries(tenant_id,farm_id,category_id,due_on);
create index financial_entries_context_operation_idx on app.financial_entries(tenant_id,farm_id,operation_id);
create table app.financial_entry_events (
 id uuid primary key, tenant_id uuid not null, farm_id uuid not null, entry_id uuid not null, operation_id uuid,
 event_type text not null, actor_user_id uuid, occurred_on date, details jsonb not null, recorded_at timestamptz not null default current_timestamp,
 constraint financial_entry_events_entry_fk foreign key(tenant_id,farm_id,entry_id) references app.financial_entries(tenant_id,farm_id,id) on delete restrict,
 constraint financial_entry_events_farm_fk foreign key(tenant_id,farm_id) references app.farms(tenant_id,id) on delete restrict,
 constraint financial_entry_events_type_check check(event_type in ('CREATED','CORRECTED','SETTLED','CANCELLED'))
);
create unique index financial_entry_events_context_id_uq on app.financial_entry_events(tenant_id,farm_id,id);
create unique index financial_entry_events_operation_uq on app.financial_entry_events(tenant_id,farm_id,entry_id,operation_id) where operation_id is not null;
create index financial_entry_events_context_entry_recorded_idx on app.financial_entry_events(tenant_id,farm_id,entry_id,recorded_at desc,id desc);
revoke all on app.financial_categories,app.financial_entries,app.financial_entry_events from public;
grant select,insert,update(name,code,kind,status,version,updated_at) on app.financial_categories to app_api;
grant select,insert,update(category_id,category_name_snapshot,description,amount,due_on,settled_on,cancelled_at,notes,status,version,updated_by_user_id,updated_at) on app.financial_entries to app_api;
grant select,insert on app.financial_entry_events to app_api;
alter table app.financial_categories enable row level security; alter table app.financial_categories force row level security;
alter table app.financial_entries enable row level security; alter table app.financial_entries force row level security;
alter table app.financial_entry_events enable row level security; alter table app.financial_entry_events force row level security;
create policy financial_categories_tenant on app.financial_categories to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
create policy financial_entries_tenant on app.financial_entries to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
create policy financial_entry_events_tenant on app.financial_entry_events to app_api using(tenant_id=app.current_tenant_id()) with check(tenant_id=app.current_tenant_id());
