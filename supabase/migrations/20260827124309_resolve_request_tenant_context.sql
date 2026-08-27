create function app.resolve_current_user_tenant_context(p_organization_id uuid, p_farm_id uuid)
returns table (organization_id uuid, organization_name text, farm_id uuid, farm_name text, membership_id uuid, role_key text, farm_scope_mode text)
language sql stable security definer set search_path = '' as $$
    select organization.id, organization.name, farm.id, farm.name, membership.id, membership.role_key, membership.farm_scope_mode
    from app.users internal_user
    join app.organization_memberships membership on membership.user_id = internal_user.id and membership.tenant_id = p_organization_id
    join app.organizations organization on organization.id = membership.tenant_id
    join app.farms farm on farm.id = p_farm_id and farm.tenant_id = organization.id
    left join app.membership_farm_scopes scope on scope.tenant_id = membership.tenant_id and scope.membership_id = membership.id and scope.farm_id = farm.id
    where internal_user.id = app.current_user_id() and internal_user.status = 'ACTIVE'
      and membership.status = 'ACTIVE' and organization.status = 'ACTIVE' and farm.status = 'ACTIVE'
      and (membership.farm_scope_mode = 'ALL_FARMS' or scope.membership_id is not null)
$$;
revoke all on function app.resolve_current_user_tenant_context(uuid, uuid) from public;
do $$ begin
 if exists (select 1 from pg_catalog.pg_roles where rolname = 'anon') then revoke all on function app.resolve_current_user_tenant_context(uuid, uuid) from anon; end if;
 if exists (select 1 from pg_catalog.pg_roles where rolname = 'authenticated') then revoke all on function app.resolve_current_user_tenant_context(uuid, uuid) from authenticated; end if;
end $$;
grant execute on function app.resolve_current_user_tenant_context(uuid, uuid) to app_api;
