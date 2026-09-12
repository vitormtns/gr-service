# ADR 0028 — Reprodução, parto e linhagem materna

## Estado

Aceita para a Phase 09B.

## Decisão

Gestação é um agregado tenant-aware com contexto histórico da fazenda de serviço. A leitura e a mutação exigem acesso à fazenda de custódia atual; transferências não duplicam nem reescrevem a gestação. Relações maternas são tenant-wide, mas não concedem acesso transitivo entre mãe e cria.

As mutações usam `operationId`, bloqueio transacional por operação e eventos tipados para replay. O parto grava `CREATED` para representar a criação do agregado da cria e `BORN` para representar o fato pecuário do nascimento; ambos são necessários e não são eventos duplicados.

## Consequências

Uma cria possui no máximo uma mãe. O parto e a relação materna participam da mesma transação: uma falha reverte cria, gestação, versões e eventos. Replays corretos preservam os identificadores originais e não incrementam versões.
