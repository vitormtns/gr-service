# ADR 0024 — Núcleo de estoque e insumos

O catálogo de produtos pertence ao tenant e define uma única unidade base explícita. Locais, saldos e movimentos pertencem à fazenda atual. O saldo é uma projeção atual em `inventory_balances`; o ledger append-only é histórico e não configura Event Sourcing.

Movimentos usam quantidade decimal positiva (`NUMERIC(19,6)`), snapshots de produto/local e `operationId` idempotente por tenant, fazenda e operação. A mesma semântica devolve o movimento original; uma semântica distinta conflita. A transação toma um advisory lock transacional para a operação e bloqueia produto, locais e linhas de saldo em ordem lexicográfica de UUID. A criação do saldo é `INSERT ... ON CONFLICT DO NOTHING` seguida de `SELECT ... FOR UPDATE`, evitando corrida de criação e atualização perdida. Transferências alteram ambos os saldos nessa mesma ordem e inserem uma única linha de ledger na mesma transação; uma falha no ledger desfaz integralmente os saldos e versões.

Produtos e locais inativos não recebem movimentos. A inativação só é permitida com saldo total zero, sob os mesmos locks de produto/local usados pelos movimentos, tornando a competição serializável. RLS com `FORCE`, grants mínimos e predicados explícitos isolam tenant e fazenda. O ledger é append-only para `app_api`; o registro interno de operações concede somente o privilégio de lock necessário para o replay idempotente.

Fora de escopo: fornecedores, compras, financeiro, custos, lotes, validade, séries, consumo animal/agricultura, transferências entre fazendas, reservas, inventário físico e conversão de unidades.
