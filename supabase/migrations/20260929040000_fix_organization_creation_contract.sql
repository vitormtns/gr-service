-- Qualifica colunas que conflitam com parâmetros OUT e permite repetir o mesmo cadastro.
create or replace function app.create_organization_for_current_user(p_id uuid, p_name text)
returns table (id uuid, name text, status text, version bigint)
language plpgsql
security definer
set search_path = ''
as $$
declare v_user uuid := app.current_user_id();
begin
    if v_user is null or not exists (
        select 1 from app.users as internal_user where internal_user.id=v_user and internal_user.status='ACTIVE'
    ) then
        raise exception 'authenticated active user required';
    end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('create-organization:' || p_id::text, 0));
    if exists (select 1 from app.organizations as organization where organization.id=p_id) then
        if not exists (
            select 1 from app.platform_admin_events as event
            join app.organization_memberships as membership
              on membership.tenant_id=event.organization_id and membership.user_id=event.actor_user_id
            where event.organization_id=p_id and event.event_type='ORGANIZATION_CREATED'
              and event.actor_user_id=v_user and event.details->>'name'=p_name
              and membership.status='ACTIVE' and membership.role_key='OWNER'
        ) then
            raise exception 'organization command conflict' using errcode='23505';
        end if;
        return query select organization.id,organization.name,organization.status,organization.version
                     from app.organizations as organization where organization.id=p_id;
        return;
    end if;
    insert into app.organizations(id,name,status) values(p_id,p_name,'ACTIVE');
    insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode)
    values(pg_catalog.gen_random_uuid(),p_id,v_user,'OWNER','ACTIVE','ALL_FARMS');
    insert into app.platform_admin_events(id,organization_id,actor_user_id,event_type,details)
    values(pg_catalog.gen_random_uuid(),p_id,v_user,'ORGANIZATION_CREATED',pg_catalog.jsonb_build_object('name',p_name));
    return query select organization.id,organization.name,organization.status,organization.version
                 from app.organizations as organization where organization.id=p_id;
end
$$;
revoke all on function app.create_organization_for_current_user(uuid,text) from public;
grant execute on function app.create_organization_for_current_user(uuid,text) to app_api;
