# ADR 0030 — Planejador operacional do rebanho

## Contexto e decisão

O planejador persiste intenções operacionais humanas. O trabalho pendente, por sua vez, continua sendo calculado a partir de fatos do domínio. São conceitos deliberadamente diferentes:

- `planner`: intenção persistida, criada por uma pessoa;
- `pending-work`: condição derivada de pesagens, tratamentos, reprodução e demais fatos reais.

Concluir um item do planejador não registra uma pesagem, vacinação, vermifugação, movimentação ou parto. Também não cria animal, relação materna nem evento de domínio. A mutação afeta somente `herd_planner_items` e `herd_planner_operations`.

## Ciclo de vida e concorrência

O ciclo permitido é `OPEN -> COMPLETED` ou `OPEN -> CANCELLED`. Os estados terminais não podem ser reabertos nem corrigidos nesta fase. Cada alteração exige a versão esperada e incrementa a versão exatamente uma vez.

Todas as mutações possuem `operationId`. O histórico imutável em `herd_planner_operations` guarda o tipo da operação, o item, a semântica canônica do comando e a versão resultante. O bloqueio transacional por operação serializa requisições concorrentes. Assim, a mesma operação e a mesma semântica produzem replay; reutilizar a operação com outro tipo, item ou conteúdo produz conflito. Operações antigas continuam reproduzíveis depois de correções e transições posteriores.

## Agenda unificada

A agenda é um modelo somente de leitura composto no PostgreSQL:

```text
itens manuais OPEN
UNION ALL
trabalho pendente derivado
-> filtros
-> ordenação total
-> LIMIT/OFFSET
```

Os filtros são aplicados antes da paginação global. A ordem é `operationalDate`, `source`, `kind` e `stableId`, portanto não depende da ordem física das linhas. A consulta é set-based, limitada, sem N+1 e sem união em memória. `UNION ALL` é intencional: uma vacinação pendente e uma vacinação planejada para o mesmo animal devem coexistir, sem deduplicação automática.

## Transferências e privacidade

Um item manual pertence à fazenda em que foi criado. Se o animal for transferido, o item permanece na origem e não é movido nem copiado. A representação histórica expõe apenas o identificador já conhecido do animal; não inclui fazenda de destino, nome atual da fazenda nem metadados protegidos de custódia.

O trabalho derivado segue a custódia atual. Após a transferência, deixa de aparecer na agenda da origem e passa a aparecer somente na fazenda atual autorizada. A nova fazenda pode criar seus próprios itens manuais para o animal.

Os vínculos das medições e dos tratamentos foram ajustados para preservar a identidade do animal e a fazenda histórica sem impedir uma transferência posterior.

## Segurança e contratos

RLS com `FORCE RLS` garante isolamento por organização, enquanto predicados explícitos dos repositórios e a resolução autorizada do contexto garantem o recorte por fazenda. As chaves estrangeiras compostas impedem que o histórico de operações aponte para item de outra organização ou fazenda. `app_api` recebe somente os privilégios mínimos necessários e não existe endpoint para enumerar o histórico de operações.

`OWNER`, `ADMIN`, `MANAGER`, `OPERATOR` e `VIEWER` podem ler. Somente os quatro primeiros podem criar, corrigir, concluir ou cancelar. Identificadores válidos de outro contexto recebem resposta não enumerável. Comandos usam JSON estrito, rejeitam campos duplicados, desconhecidos e pertencentes ao servidor; consultas validam filtros e paginação.

## Consequências

O planejador não substitui fatos reais nem automatiza execução de domínio. Essa separação evita que marcar uma intenção como concluída produza registros operacionais falsos e mantém a agenda consistente com a custódia atual e com o histórico da fazenda de origem.
