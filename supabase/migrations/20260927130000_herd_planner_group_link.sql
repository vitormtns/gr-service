-- Referência opcional a um grupo de manejo da mesma fazenda da tarefa.
alter table app.herd_planner_items add column group_id uuid;
alter table app.herd_planner_items add constraint herd_planner_items_group_fk
  foreign key (tenant_id,farm_id,group_id) references app.herd_groups(tenant_id,farm_id,id) on delete restrict;
grant update (group_id) on app.herd_planner_items to app_api;
create index herd_planner_items_group_idx on app.herd_planner_items
  (tenant_id,farm_id,group_id,scheduled_for,id) where group_id is not null;
