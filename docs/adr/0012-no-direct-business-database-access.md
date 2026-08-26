# ADR 0012 — Sem acesso direto de clientes ao banco de negócio

## Estado da decisão

Aceita.

## Contexto

Supabase permite acesso de clientes ao banco, mas regras rurais, tenancy, permissões, cotas e auditoria precisam de uma autoridade única. Web e mobile são ambientes não confiáveis.

## Decisão

Flutter e Angular chamarão `gr-service` para toda leitura ou escrita de negócio. A API autentica, resolve usuário/tenant/fazenda e aplica regras. A chave pública pode ficar em clientes para recursos aprovados; `service_role` e chaves secretas ficam somente no backend. RLS complementa a proteção.

## Consequências positivas

- Regras e autorização permanecem consistentes entre clientes.
- Auditoria, idempotência e evolução de contratos ficam centralizadas.

## Consequências negativas

- Toda operação de negócio depende da disponibilidade e latência da API.
- Recursos Supabase precisam ser expostos de forma deliberada pelo backend.

## Alternativas consideradas

- CRUD direto com RLS: rejeitado porque RLS não expressa todo o domínio.
- `service_role` no aplicativo: rejeitado por comprometer todos os dados.
- Regras duplicadas em cada frontend: rejeitadas por divergência e possibilidade de bypass.
