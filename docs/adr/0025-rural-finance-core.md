# ADR 0025 — Núcleo financeiro rural

Categorias são tenant-wide e lançamentos pertencem à fazenda atual. O núcleo suporta receitas e despesas com ciclo imutável `PENDING → SETTLED` ou `PENDING → CANCELLED`; correções só ocorrem enquanto pendente e geram histórico append-only, sem Event Sourcing. Valores usam `BigDecimal` e `NUMERIC(19,2)`, na moeda operacional BRL, sem coluna de moeda nesta fase.

`operationId` é idempotente por tenant, fazenda e operação; versões protegem patches e transições. RLS forçado, grants mínimos e predicados JDBC explícitos isolam tenant/fazenda. Categorias inativas permanecem históricas, mas não podem originar lançamentos.

Relatórios usam `dueOn` como janela, incluindo totais pendentes, liquidados e vencidos, e agregação set-based por categoria. Papéis OWNER, ADMIN e MANAGER administram categorias; OPERATOR também opera lançamentos; VIEWER só lê.

Fora de escopo: contabilidade/fiscal, bancos, pagamentos, fornecedores/clientes, rateios, parcelas, recorrência, multimoeda, anexos, conciliação e integrações com estoque ou vendas.
