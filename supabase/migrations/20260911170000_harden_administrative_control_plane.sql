-- B1: serialize administrative ownership decisions per organization.
-- The advisory key is deterministic and transaction-scoped; it cannot survive a failed request.
create function app.lock_organization_administration(p_organization_id uuid)
returns void language sql volatile set search_path = pg_catalog, app as $$
  select pg_advisory_xact_lock(hashtextextended(p_organization_id::text, 0))
$$;
revoke all on function app.lock_organization_administration(uuid) from public;
grant execute on function app.lock_organization_administration(uuid) to app_api;

-- Replace the two deliberately narrow privileged entry points with an explicit,
-- non-caller-controlled search path. Objects remain schema-qualified in bodies.
create or replace function app.create_organization_for_current_user(p_id uuid, p_name text)
returns table (id uuid, name text, status text, version bigint)
language plpgsql security definer set search_path = pg_catalog, app as $$
declare v_user uuid := app.current_user_id();
begin
  if v_user is null or not exists (select 1 from app.users where id=v_user and status='ACTIVE') then raise exception 'authenticated active user required'; end if;
  insert into app.organizations(id,name,status) values(p_id,p_name,'ACTIVE');
  insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values(gen_random_uuid(),p_id,v_user,'OWNER','ACTIVE','ALL_FARMS');
  insert into app.platform_admin_events(id,organization_id,actor_user_id,event_type,details) values(gen_random_uuid(),p_id,v_user,'ORGANIZATION_CREATED',jsonb_build_object('name',p_name));
  return query select o.id,o.name,o.status,o.version from app.organizations o where o.id=p_id;
end $$;

create or replace function app.accept_organization_invitation(p_token_hash text)
returns boolean language plpgsql security definer set search_path = pg_catalog, app as $$
declare v_inv app.organization_invitations%rowtype; v_user uuid := app.current_user_id(); v_email text;
begin
  select * into v_inv from app.organization_invitations where token_hash=p_token_hash for update;
  if not found or v_inv.status <> 'PENDING' or v_inv.expires_at <= current_timestamp then return false; end if;
  perform app.lock_organization_administration(v_inv.organization_id);
  select lower(btrim(email)) into v_email from app.users where id=v_user and status='ACTIVE';
  if v_email is null or v_email <> v_inv.normalized_email then return false; end if;
  if exists (select 1 from app.organization_memberships where tenant_id=v_inv.organization_id and user_id=v_user) then return false; end if;
  insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values(gen_random_uuid(),v_inv.organization_id,v_user,v_inv.role_key,'ACTIVE',v_inv.farm_scope_mode);
  if v_inv.farm_scope_mode='SELECTED_FARMS' then insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id) select v_inv.organization_id,m.id,s.farm_id from app.organization_memberships m join app.organization_invitation_farm_scopes s on s.invitation_id=v_inv.id where m.tenant_id=v_inv.organization_id and m.user_id=v_user; end if;
  update app.organization_invitations set status='ACCEPTED',accepted_at=current_timestamp where id=v_inv.id;
  insert into app.platform_admin_events(id,organization_id,actor_user_id,target_user_id,event_type,details) values(gen_random_uuid(),v_inv.organization_id,v_user,v_user,'INVITATION_ACCEPTED',jsonb_build_object('invitationId',v_inv.id));
  return true;
end $$;
