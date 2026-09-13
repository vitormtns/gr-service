# ADR 0028 — Reprodução, parto e linhagem materna

## Estado

Aceita para a Phase 09B.

## Decisão

Gestação é um agregado tenant-aware com contexto histórico da fazenda de serviço. A leitura e a mutação exigem acesso à fazenda de custódia atual; transferências não duplicam nem reescrevem a gestação. Relações maternas são tenant-wide, mas não concedem acesso transitivo entre mãe e cria.

As mutações usam `operationId`, bloqueio transacional por operação e eventos tipados para replay. O parto grava `CREATED` para representar a criação do agregado da cria e `BORN` para representar o fato pecuário do nascimento; ambos são necessários e não são eventos duplicados.

A fazenda gravada na gestação representa o contexto histórico do serviço. As consultas e transições de gestação verificam a fazenda atual da mãe por junção, portanto uma transferência preserva o registro original e permite a operação somente na nova custódia. A referência opcional da relação materna à gestação é composta por `tenant_id` e `pregnancy_id`, impedindo associações entre tenants.

## Consequências

Uma cria possui no máximo uma mãe. O parto e a relação materna participam da mesma transação: uma falha reverte cria, gestação, versões e eventos. Replays corretos preservam os identificadores originais e não incrementam versões.

## Hardening final da Phase 09B

As transações de cobertura, confirmação, encerramento e parto foram verificadas com PostgreSQL real e injeção de falha após escritas parciais. O rollback remove integralmente alterações de estado, versões, cria, relação materna, eventos e resíduos de idempotência; o retry com o mesmo `operationId` permanece válido.

Quando o parto referencia uma gestação, a gestação é bloqueada antes da mãe. Essa ordem é compartilhada com as transições de gestação e evita deadlock entre parto e encerramento concorrentes. Partos concorrentes com operações diferentes continuam limitados a uma única cria, relação materna e transição `CALVED`.

O replay de cobertura é vinculado à mãe e compara o payload semântico normalizado. As respostas HTTP usam DTO público e não expõem contexto de tenant, fazenda, `operationId` nem payload canônico interno. A tabela de relações maternas é append-only para `app_api`, e constraints de banco rejeitam combinações inválidas entre status, datas, motivo de encerramento e cria.

As provas HTTP cobrem a matriz de papéis, optimistic locking, não enumeração com identificadores externos reais e autorização baseada na custódia atual após transferências. A relação materna permanece tenant-wide sem conceder acesso transitivo entre mãe e cria.
