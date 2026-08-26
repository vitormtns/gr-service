# ADR 0009 — Tenant como chave futura de distribuição

## Estado da decisão

Aceita como direção reversível, sem particionamento atual.

## Contexto

Grande parte das operações pertence a uma organização e não cruza tenants. Caso o volume cresça, essa localidade pode orientar particionamento de dados e trabalho assíncrono.

## Decisão

Propagar `tenant_id` em dados, eventos e jobs para permitir seu uso futuro como chave de partição, filas ou sharding. Não particionar nem criar shards antes de métricas de volume e distribuição.

## Consequências positivas

- Preserva localidade e uma rota de escala previsível.
- Facilita limitar impacto e consumo por tenant.

## Consequências negativas

- Tenants muito grandes podem causar partições desbalanceadas.
- Operações administrativas entre tenants precisarão de estratégia própria.

## Alternativas consideradas

- Distribuição aleatória: perde localidade operacional.
- Fazenda como chave: fragmenta organizações com várias fazendas.
- Particionamento imediato: rejeitado sem dados de carga.
