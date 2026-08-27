create function app.current_user_id()
returns uuid
language sql
stable
parallel safe
set search_path = ''
as $$
    select nullif(pg_catalog.current_setting('app.current_user_id', true), '')::uuid
$$;

revoke all on function app.current_user_id() from public;
grant execute on function app.current_user_id() to app_api;

create function app.list_current_user_organizations()
returns table (
    organization_id uuid,
    organization_name text,
    membership_id uuid,
    role_key text,
    farm_scope_mode text
)
language sql
stable
security definer
set search_path = ''
as $$
    select
        organization.id,
        organization.name,
        membership.id,
        membership.role_key,
        membership.farm_scope_mode
    from app.users as internal_user
    join app.organization_memberships as membership
        on membership.user_id = internal_user.id
    join app.organizations as organization
        on organization.id = membership.tenant_id
    where internal_user.id = app.current_user_id()
      and internal_user.status = 'ACTIVE'
      and membership.status = 'ACTIVE'
      and organization.status = 'ACTIVE'
    order by organization.name asc, organization.id asc, membership.id asc
$$;

revoke all on function app.list_current_user_organizations() from public;

do $$
begin
    if exists (select 1 from pg_catalog.pg_roles where rolname = 'anon') then
        revoke all on function app.list_current_user_organizations() from anon;
    end if;
    if exists (select 1 from pg_catalog.pg_roles where rolname = 'authenticated') then
        revoke all on function app.list_current_user_organizations() from authenticated;
    end if;
end
$$;

grant execute on function app.list_current_user_organizations() to app_api;
