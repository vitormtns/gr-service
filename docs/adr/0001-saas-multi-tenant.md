# ADR 0001 — SaaS multi-tenant

## Estado da decisão

Aceita.

## Contexto

O Gerenciador Rural atenderá clientes independentes na mesma aplicação. Cada cliente precisa isolar pessoas, fazendas e dados operacionais, enquanto a operação inicial deve permanecer simples.

## Decisão

O produto será SaaS multi-tenant. A futura entidade `Organization` será a unidade de tenancy e poderá possuir várias fazendas. Identidade de fazenda não substitui identidade de tenant.

## Consequências positivas

- Um limite uniforme serve autorização, consultas, auditoria e evolução de escala.
- Uma organização pode administrar várias propriedades sem duplicar conta e assinatura.

## Consequências negativas

- Toda operação exige resolução e propagação corretas do tenant.
- Falhas de filtro representam risco de vazamento entre clientes e exigem testes específicos.

## Alternativas consideradas

- Fazenda como tenant: rejeitada porque não representa grupos com várias fazendas.
- Instância isolada por cliente: rejeitada pelo custo operacional inicial.
- Aplicação single-tenant: incompatível com o modelo SaaS previsto.
