-- Phase 08: administrative control plane. Existing tenancy entities remain the source of truth.
create table app.organization_invitations (
    id uuid primary key,
    organization_id uuid not null references app.organizations(id) on delete restrict,
    invited_email text not null,
    normalized_email text not null,
    role_key text not null check (role_key in ('OWNER','ADMIN','MANAGER','OPERATOR','VIEWER')),
    farm_scope_mode text not null check (farm_scope_mode in ('ALL_FARMS','SELECTED_FARMS')),
    status text not null check (status in ('PENDING','ACCEPTED','REVOKED')),
    token_hash text not null unique,
    invited_by_user_id uuid not null references app.users(id) on delete restrict,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    constraint organization_invitations_email_check check (length(btrim(invited_email)) > 0)
);

create table app.organization_invitation_farm_scopes (
    invitation_id uuid not null references app.organization_invitations(id) on delete restrict,
    farm_id uuid not null,
    primary key (invitation_id, farm_id),
    foreign key (farm_id) references app.farms(id) on delete restrict
);

create table app.platform_admin_events (
    id uuid primary key,
    organization_id uuid not null references app.organizations(id) on delete restrict,
    farm_id uuid,
    actor_user_id uuid not null references app.users(id) on delete restrict,
    target_user_id uuid,
    event_type text not null,
    details jsonb not null default '{}'::jsonb,
    recorded_at timestamptz not null default current_timestamp
);

create index organization_memberships_tenant_role_idx on app.organization_memberships(tenant_id, role_key);
create index organization_invitations_organization_status_idx on app.organization_invitations(organization_id, status);
create index organization_invitations_expiry_idx on app.organization_invitations(expires_at);
create index platform_admin_events_organization_recorded_idx on app.platform_admin_events(organization_id, recorded_at desc, id desc);
create index platform_admin_events_organization_type_recorded_idx on app.platform_admin_events(organization_id, event_type, recorded_at desc);

grant select, insert, update, delete on app.organization_invitations, app.organization_invitation_farm_scopes, app.platform_admin_events to app_api;
alter table app.organization_invitations enable row level security;
alter table app.organization_invitations force row level security;
alter table app.organization_invitation_farm_scopes enable row level security;
alter table app.organization_invitation_farm_scopes force row level security;
alter table app.platform_admin_events enable row level security;
alter table app.platform_admin_events force row level security;
create policy organization_invitations_tenant_isolation on app.organization_invitations for all to app_api using (organization_id = app.current_tenant_id()) with check (organization_id = app.current_tenant_id());
create policy organization_invitation_farm_scopes_tenant_isolation on app.organization_invitation_farm_scopes for all to app_api using (exists (select 1 from app.organization_invitations i where i.id = invitation_id and i.organization_id = app.current_tenant_id())) with check (exists (select 1 from app.organization_invitations i where i.id = invitation_id and i.organization_id = app.current_tenant_id()));
create policy platform_admin_events_tenant_isolation on app.platform_admin_events for all to app_api using (organization_id = app.current_tenant_id()) with check (organization_id = app.current_tenant_id());

create function app.create_organization_for_current_user(p_id uuid, p_name text)
returns table (id uuid, name text, status text, version bigint)
language plpgsql security definer set search_path = '' as $$
declare v_user uuid := app.current_user_id();
begin
  if v_user is null or not exists (select 1 from app.users where id=v_user and status='ACTIVE') then raise exception 'authenticated active user required'; end if;
  insert into app.organizations(id,name,status) values(p_id,p_name,'ACTIVE');
  insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values(gen_random_uuid(),p_id,v_user,'OWNER','ACTIVE','ALL_FARMS');
  insert into app.platform_admin_events(id,organization_id,actor_user_id,event_type,details) values(gen_random_uuid(),p_id,v_user,'ORGANIZATION_CREATED',jsonb_build_object('name',p_name));
  return query select o.id,o.name,o.status,o.version from app.organizations o where o.id=p_id;
end $$;
revoke all on function app.create_organization_for_current_user(uuid,text) from public;
grant execute on function app.create_organization_for_current_user(uuid,text) to app_api;

create function app.accept_organization_invitation(p_token_hash text)
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_inv app.organization_invitations%rowtype; v_user uuid := app.current_user_id(); v_email text;
begin
  select * into v_inv from app.organization_invitations where token_hash=p_token_hash for update;
  if not found or v_inv.status <> 'PENDING' or v_inv.expires_at <= current_timestamp then return false; end if;
  select lower(btrim(email)) into v_email from app.users where id=v_user and status='ACTIVE';
  if v_email is null or v_email <> v_inv.normalized_email then return false; end if;
  if exists (select 1 from app.organization_memberships where tenant_id=v_inv.organization_id and user_id=v_user) then return false; end if;
  insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values(gen_random_uuid(),v_inv.organization_id,v_user,v_inv.role_key,'ACTIVE',v_inv.farm_scope_mode);
  if v_inv.farm_scope_mode='SELECTED_FARMS' then
    insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id)
    select v_inv.organization_id,m.id,s.farm_id from app.organization_memberships m join app.organization_invitation_farm_scopes s on s.invitation_id=v_inv.id where m.tenant_id=v_inv.organization_id and m.user_id=v_user;
  end if;
  update app.organization_invitations set status='ACCEPTED',accepted_at=current_timestamp where id=v_inv.id;
  insert into app.platform_admin_events(id,organization_id,actor_user_id,target_user_id,event_type,details) values(gen_random_uuid(),v_inv.organization_id,v_user,v_user,'INVITATION_ACCEPTED',jsonb_build_object('invitationId',v_inv.id));
  return true;
end $$;
revoke all on function app.accept_organization_invitation(text) from public;
grant execute on function app.accept_organization_invitation(text) to app_api;
