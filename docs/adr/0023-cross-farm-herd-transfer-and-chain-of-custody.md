# ADR 0023 — Transferência entre fazendas e cadeia de custódia

## Decisão

Uma transferência mantém o mesmo `animals.id` e `tenant_id`; somente a custódia atual (`farm_id`) e, opcionalmente, o `paddock_id` são alterados. O estado continua `ACTIVE`.

`animal_events` e a projeção de movimentos passam a referenciar a identidade `tenant_id + animal_id`, enquanto `farm_id` permanece uma referência histórica imutável. A timeline continua estritamente por fazenda: origem recebe `TRANSFERRED_OUT`; destino recebe `TRANSFERRED_IN` e eventos posteriores.

`animal_transfers` é um ledger append-only com snapshots de fazendas/piquete, versões, ator e observações. Consultas são limitadas à fazenda de origem ou destino; RLS continua tenant-based como defesa adicional.

## Consistência

A operação é idempotente por `tenant_id + operation_id`, com payload canônico e lote ordenado por UUID. A transação bloqueia a operação, fazendas em ordem de UUID, piquete de destino e animais em ordem determinística. A verificação de versão, estado ativo, acesso do ator à origem e destino e unicidade da identificação precedem a mutação. Ledger, alteração de custódia e ambos os eventos são atômicos.

O piquete de destino deve ser ativo e pertencer à fazenda destino. Sem piquete informado, a localização atual fica nula. As FKs e índices protegem identidade, contexto histórico, tenant/fazenda, ledger e consultas por origem, destino, animal e operação.

## Fora de escopo

Transferência entre tenants, clonagem, copropriedade, cancelamento/undo, aprovação em duas etapas, logística, documentos fiscais e financeiro.
