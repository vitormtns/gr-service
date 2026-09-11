# ADR 0024 — Núcleo de estoque e insumos

O catálogo de produtos pertence ao tenant e define uma única unidade base explícita. Locais, saldos e movimentos pertencem à fazenda atual. O saldo é uma projeção atual em `inventory_balances`; o ledger append-only é histórico e não configura Event Sourcing.

Movimentos usam quantidade decimal positiva (`NUMERIC(19,6)`), snapshots de produto/local e `operationId` idempotente por tenant, fazenda e operação. A mesma semântica devolve o movimento original; uma semântica distinta conflita. A transação bloqueia a operação, produto e locais ordenados por UUID e atualiza os saldos sob lock, impedindo estoque negativo. Transferências alteram ambos os saldos e inserem uma única linha de ledger atomicamente.

Produtos e locais inativos não recebem movimentos. A inativação só é permitida com saldo total zero, sob os mesmos locks. RLS com `FORCE`, grants mínimos e predicados explícitos isolam tenant e fazenda.

Fora de escopo: fornecedores, compras, financeiro, custos, lotes, validade, séries, consumo animal/agricultura, transferências entre fazendas, reservas, inventário físico e conversão de unidades.
