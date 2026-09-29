-- Diretório administrativo do próprio usuário, inclusive para reativação de organizações.
-- Não seleciona tenant, não concede escrita e preserva o bootstrap operacional somente ativo.
create function app.list_current_user_administrative_organizations()
returns table (
    id uuid,
    name text,
    status text,
    version bigint,
    role_key text,
    farm_scope_mode text
)
language sql
stable
security definer
set search_path = ''
as $$
    select organization.id, organization.name, organization.status, organization.version,
           membership.role_key, membership.farm_scope_mode
    from app.users as internal_user
    join app.organization_memberships as membership on membership.user_id = internal_user.id
    join app.organizations as organization on organization.id = membership.tenant_id
    where internal_user.id = app.current_user_id()
      and internal_user.status = 'ACTIVE'
      and membership.status = 'ACTIVE'
    order by organization.name, organization.id
$$;

revoke all on function app.list_current_user_administrative_organizations() from public;
do $$
begin
    if exists (select 1 from pg_catalog.pg_roles where rolname = 'anon') then
        revoke all on function app.list_current_user_administrative_organizations() from anon;
    end if;
    if exists (select 1 from pg_catalog.pg_roles where rolname = 'authenticated') then
        revoke all on function app.list_current_user_administrative_organizations() from authenticated;
    end if;
end
$$;
grant execute on function app.list_current_user_administrative_organizations() to app_api;
