# ADR 0003 — Tenancy em PostgreSQL compartilhado

## Estado da decisão

Aceita.

## Contexto

A operação inicial usará PostgreSQL no Supabase e precisa equilibrar isolamento lógico, custo e facilidade de migrations.

## Decisão

Tenants compartilharão banco e schema. Tabelas de negócio carregarão `tenant_id`, constraints e índices coerentes; consultas operacionais sempre filtrarão o tenant validado. RLS será defesa adicional, não a única autorização.

## Consequências positivas

- Um único conjunto de migrations e infraestrutura reduz custo operacional.
- Consultas e manutenção entre tenants autorizadas permanecem possíveis no backend.

## Consequências negativas

- Erros de consulta podem atravessar tenants se as proteções falharem.
- Índices e constraints compostos precisam considerar `tenant_id`.

## Alternativas consideradas

- Banco por tenant: isolamento maior, mas provisionamento e migrations mais caros.
- Schema por tenant: multiplica objetos e dificulta evolução uniforme.
- Apenas RLS: rejeitado porque regras de domínio e autorização pertencem também à API.
