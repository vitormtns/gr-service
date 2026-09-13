# ADR 0029 — Trabalho pendente derivado do rebanho

## Decisão

O trabalho pendente é um read model derivado; não há tabela de alertas, notificações persistidas nem scheduler. A data de referência vem de `Clock` (UTC em produção), permitindo testes determinísticos. Vacinação e vermifugação consideram exclusivamente o último fato por animal e tipo, e somente quando `nextDueOn` foi informado. Pesagem é devida se o animal ativo nunca foi pesado ou se a última pesagem é anterior ou igual ao corte de 90 dias. Partos usam apenas gestações abertas e a custódia atual da mãe.

## Consulta e ordenação

A infraestrutura usa CTEs e `DISTINCT ON` para obter os últimos fatos de peso e saúde, reunindo resultados por `UNION ALL`. Os índices de Phase 09C suportam esse padrão. A ordenação é estável por data, tipo, animal e identificador de gestação; a paginação ocorre no banco.
