create function app.list_current_user_accessible_farms(p_organization_id uuid)
returns table (
    farm_id uuid,
    farm_name text
)
language sql
stable
security definer
set search_path = ''
as $$
    select
        farm.id,
        farm.name
    from app.users as internal_user
    join app.organization_memberships as membership
        on membership.user_id = internal_user.id
       and membership.tenant_id = p_organization_id
    join app.organizations as organization
        on organization.id = membership.tenant_id
    join app.farms as farm
        on farm.tenant_id = organization.id
    left join app.membership_farm_scopes as farm_scope
        on farm_scope.tenant_id = membership.tenant_id
       and farm_scope.membership_id = membership.id
       and farm_scope.farm_id = farm.id
    where internal_user.id = app.current_user_id()
      and internal_user.status = 'ACTIVE'
      and membership.status = 'ACTIVE'
      and organization.status = 'ACTIVE'
      and farm.status = 'ACTIVE'
      and (
          membership.farm_scope_mode = 'ALL_FARMS'
          or farm_scope.membership_id is not null
      )
    order by farm.name asc, farm.id asc
$$;

revoke all on function app.list_current_user_accessible_farms(uuid) from public;

do $$
begin
    if exists (select 1 from pg_catalog.pg_roles where rolname = 'anon') then
        revoke all on function app.list_current_user_accessible_farms(uuid) from anon;
    end if;
    if exists (select 1 from pg_catalog.pg_roles where rolname = 'authenticated') then
        revoke all on function app.list_current_user_accessible_farms(uuid) from authenticated;
    end if;
end
$$;

grant execute on function app.list_current_user_accessible_farms(uuid) to app_api;
