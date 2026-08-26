# ADR 0013 — Schema privado para dados da aplicação

## Estado da decisão

Aceita.

## Contexto

O Supabase expõe pelos serviços de dados apenas os schemas configurados no PostgREST. As tabelas do domínio precisam permanecer sob controle da API Spring, com privilégios mínimos e isolamento complementar por RLS.

## Decisão

Criar tabelas de negócio no schema privado `app`, sem adicioná-lo à lista de schemas expostos. Revogar privilégios de `PUBLIC` e conceder à role `app_api` somente uso do schema, execução da função de contexto e operações necessárias nas tabelas da aplicação. A role será `NOLOGIN`, sem superusuário e sem `BYPASSRLS`.

O tenant ativo será informado pela API dentro da transação por `set_config('app.current_tenant_id', '<uuid>', true)`. Políticas RLS usarão `app.current_tenant_id()` como defesa complementar; repositories ainda deverão filtrar explicitamente por tenant.

## Consequências positivas

- Reduz a exposição acidental das tabelas pelo PostgREST.
- Separa objetos de negócio dos schemas internos do Supabase.
- Permite grants e políticas específicos para o backend.

## Consequências negativas

- A API precisará configurar role e tenant em cada transação de negócio.
- Operações administrativas e testes precisam distinguir a conexão proprietária da conexão sujeita à RLS.

## Alternativas consideradas

- Tabelas em `public`: rejeitadas pelo risco de exposição e mistura com objetos públicos.
- Policies para `anon` e `authenticated`: rejeitadas porque clientes não acessam o domínio diretamente.
- Apenas filtros na aplicação: rejeitados por não oferecer defesa adicional no banco.
