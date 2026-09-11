# ADR 0022 — Território da fazenda e movimentação do rebanho

## Contexto e decisão

A Fase 05E introduz o piquete como unidade territorial operacional diretamente ligada à fazenda. Não há hierarquia de retiro ou módulo, nem transferência entre fazendas. `app.animals` continua a fonte de verdade para fazenda, status, versão e localização atual, agora por `paddock_id` anulável; registros anteriores permanecem sem localização fabricada.

Piquetes são tenant/farm scoped, com UUID fornecido pelo cliente, nomes e códigos únicos por normalização sem diferenciar caixa após trim POSIX das bordas. O nome e o código preservam caixa e whitespace interno. O status é `ACTIVE` ou `INACTIVE`; um piquete ocupado não pode ser inativado.

## Movimentação

Uma movimentação é sempre interna à mesma fazenda. Animais `ACTIVE` podem receber a primeira alocação ou mover-se entre piquetes `ACTIVE`; destino igual, status terminal, piquete inativo ou contexto externo conflitam. A atualização de `paddock_id`, incremento de versão e evento `MOVED` ocorrem na mesma transação tenant-aware.

`MOVED` registra snapshots dos nomes de origem/destino e observações normalizadas. A timeline permanece unificada e tipada. Para leitura operacional por piquete, `app.herd_movements` é uma projeção relacional append-only, referenciada pelo evento: evita varredura JSONB e não transforma o módulo em Event Sourcing.

Movimentos individuais e em lote usam `operationId` client-generated. `app.herd_movement_operations` retém a semântica canônica do comando; replay idêntico retorna o estado já gravado e divergência retorna conflito. Em lote, a lista é ordenada por UUID na forma canônica, portanto sua ordem de envio não muda a semântica. O lote tem no máximo 100 animais e é all-or-nothing.

## Concorrência, segurança e acesso

A ordem de locks é: advisory lock da operação, linha do piquete de destino e animais em UUID ascendente. A inativação também bloqueia a linha do piquete antes de contar ocupação; assim não termina com animal em piquete inativo. O lock otimista do animal é conferido depois do replay e antes do estado da movimentação.

CREATE/PATCH de piquetes permite OWNER, ADMIN e MANAGER. Leitura permite todos os papéis. Movimento permite OWNER, ADMIN, MANAGER e OPERATOR. RLS com `FORCE ROW LEVEL SECURITY`, predicates explícitos de tenant/fazenda, FKs compostas e grants mínimos mantêm a defesa em profundidade. Não há `SECURITY DEFINER`, `BYPASSRLS`, grant a PUBLIC ou alteração de `farm_id`.

## Fora de escopo

Transferência entre fazendas, cadeia de custódia, visibilidade de origem/destino entre fazendas, módulos territoriais, capacidade de lotação e Event Sourcing são posteriores.
