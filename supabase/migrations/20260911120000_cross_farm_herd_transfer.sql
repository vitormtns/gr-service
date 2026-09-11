-- Phase 05F: custody changes preserve animal identity while retaining farm-scoped history.
alter table app.animals add constraint animals_tenant_id_id_unique unique (tenant_id, id);
alter table app.animal_events drop constraint animal_events_animal_fk;
alter table app.animal_events add constraint animal_events_animal_identity_fk foreign key (tenant_id, animal_id) references app.animals (tenant_id, id) on delete restrict;
alter table app.animal_events add constraint animal_events_historical_farm_fk foreign key (tenant_id, farm_id) references app.farms (tenant_id, id) on delete restrict;
alter table app.herd_movements drop constraint herd_movements_animal_fk;
alter table app.herd_movements add constraint herd_movements_animal_identity_fk foreign key (tenant_id, animal_id) references app.animals (tenant_id, id) on delete restrict;
alter table app.animal_events drop constraint animal_events_type_check;
alter table app.animal_events drop constraint animal_events_operation_check;
alter table app.animal_events add constraint animal_events_type_check check (event_type in ('CREATED','CORRECTED','SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN'));
alter table app.animal_events add constraint animal_events_operation_check check ((event_type in ('SOLD','DECEASED','MOVED','TRANSFERRED_OUT','TRANSFERRED_IN')) = (operation_id is not null));

create table app.herd_transfer_operations (
    tenant_id uuid not null,
    operation_id uuid not null,
    command_payload text not null,
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, operation_id),
    constraint herd_transfer_operations_tenant_fk foreign key (tenant_id) references app.organizations(id) on delete restrict
);
revoke all on app.herd_transfer_operations from public;
grant select, insert on app.herd_transfer_operations to app_api;
alter table app.herd_transfer_operations enable row level security;
alter table app.herd_transfer_operations force row level security;
create policy herd_transfer_operations_tenant_select on app.herd_transfer_operations for select to app_api using (tenant_id=app.current_tenant_id());
create policy herd_transfer_operations_tenant_insert on app.herd_transfer_operations for insert to app_api with check (tenant_id=app.current_tenant_id());

create table app.animal_transfers (
    id uuid primary key,
    tenant_id uuid not null,
    animal_id uuid not null,
    operation_id uuid not null,
    source_farm_id uuid not null,
    destination_farm_id uuid not null,
    source_farm_name text not null,
    destination_farm_name text not null,
    destination_paddock_id uuid,
    destination_paddock_name text,
    occurred_on date not null,
    actor_user_id uuid,
    original_expected_version bigint not null,
    resulting_version bigint not null,
    notes text,
    recorded_at timestamptz not null default current_timestamp,
    constraint animal_transfers_animal_fk foreign key (tenant_id, animal_id) references app.animals (tenant_id,id) on delete restrict,
    constraint animal_transfers_source_farm_fk foreign key (tenant_id,source_farm_id) references app.farms (tenant_id,id) on delete restrict,
    constraint animal_transfers_destination_farm_fk foreign key (tenant_id,destination_farm_id) references app.farms (tenant_id,id) on delete restrict,
    constraint animal_transfers_destination_paddock_fk foreign key (tenant_id,destination_farm_id,destination_paddock_id) references app.paddocks (tenant_id,farm_id,id) on delete restrict,
    constraint animal_transfers_operation_animal_unique unique (tenant_id,operation_id,animal_id),
    constraint animal_transfers_different_farms check (source_farm_id<>destination_farm_id),
    constraint animal_transfers_version_check check (original_expected_version>=0 and resulting_version=original_expected_version+1),
    constraint animal_transfers_paddock_snapshot_check check ((destination_paddock_id is null and destination_paddock_name is null) or (destination_paddock_id is not null and destination_paddock_name is not null))
);
revoke all on app.animal_transfers from public;
grant select, insert on app.animal_transfers to app_api;
alter table app.animal_transfers enable row level security;
alter table app.animal_transfers force row level security;
create policy animal_transfers_tenant_select on app.animal_transfers for select to app_api using (tenant_id=app.current_tenant_id());
create policy animal_transfers_tenant_insert on app.animal_transfers for insert to app_api with check (tenant_id=app.current_tenant_id());
create index animal_transfers_source_recorded_idx on app.animal_transfers (tenant_id,source_farm_id,recorded_at desc,id desc);
create index animal_transfers_destination_recorded_idx on app.animal_transfers (tenant_id,destination_farm_id,recorded_at desc,id desc);
create index animal_transfers_animal_recorded_idx on app.animal_transfers (tenant_id,animal_id,recorded_at desc,id desc);
create index animal_transfers_operation_idx on app.animal_transfers (tenant_id,operation_id);
grant update (farm_id,paddock_id,version,updated_at) on app.animals to app_api;
