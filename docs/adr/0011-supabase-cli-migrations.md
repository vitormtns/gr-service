# ADR 0011 — Migrations pelo Supabase CLI

## Estado da decisão

Aceita.

## Contexto

O PostgreSQL inicial será fornecido pelo Supabase. Mudanças manuais no painel não são reproduzíveis entre desenvolvimento, teste e produção.

## Decisão

Versionar SQL em `supabase/migrations` e usar Supabase CLI para criar, aplicar e validar migrations localmente. Hibernate não alterará o schema; se JPA entrar, `ddl-auto` será `validate`.

## Consequências positivas

- Histórico do schema é revisável e reproduzível.
- Banco local pode ser reconstruído com `supabase db reset`.

## Consequências negativas

- Alterações urgentes também exigem migration e fluxo de entrega.
- A equipe precisa manter compatibilidade entre aplicação e versões do schema.

## Alternativas consideradas

- Hibernate `update`: rejeitado por mudanças implícitas e não auditáveis.
- Cliques no painel: rejeitados por divergência de ambientes.
- Flyway paralelo ao Supabase: rejeitado nesta fase para não haver dois donos do schema.
