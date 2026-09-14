# ADR 0032 — Dashboard e insights operacionais do rebanho

## Estado

Aceita para a Phase 10C.

## Decisão

A API oferece três modelos de leitura tipados sob `/api/v1/herd/dashboard`:
`overview`, `activity` e `attention`. O dashboard responde de forma resumida como está a
fazenda e o que exige atenção; os relatórios da Phase 10B permanecem responsáveis pela
análise detalhada e paginada. Não existe mecanismo genérico de cards, tabela de dashboard,
snapshot persistido, cache funcional, consumidor de eventos ou agendador.

O fluxo preserva `Controller -> serviço de consulta -> portas -> JDBC`. As consultas são
set-based e bounded, executadas no mesmo snapshot transacional tenant-aware. Elas reutilizam
as fontes, os índices e a primitive SQL de trabalho pendente das Phases 09 e 10. Não há
chamadas HTTP internas, controller chamando controller, paralelismo assíncrono ou N+1.

## Períodos e relógio

Os períodos são `TODAY`, `LAST_7_DAYS`, `LAST_30_DAYS` e `CUSTOM`. A ausência de `period`
seleciona `LAST_30_DAYS`. Datas são inclusivas: hoje; hoje menos seis dias até hoje; e hoje
menos 29 dias até hoje. `CUSTOM` exige `from` e `to`; os demais rejeitam essas datas para não
aceitar uma consulta ambígua. O intervalo customizado é limitado a 366 dias, mantendo no
máximo 366 buckets diários. Todos os limites relativos usam o `Clock` injetado e os testes usam
`Clock.fixed`.

## Estado atual e histórico

O snapshot usa custódia e localização atuais de animais `ACTIVE`; `SOLD` e `DECEASED` são
excluídos. Atividade usa a fazenda imutável em que cada fato ocorreu, portanto continua na
origem após uma transferência. `BORN` é o único fato contado como nascimento e como cria
nascida; `CREATED` não entra. `CALVED` conta partos separadamente. A fazenda de origem vê a
quantidade de saídas e a de destino vê a quantidade de entradas, sem exposição de estado,
nome ou localização protegidos da contraparte.

Os filtros de categoria, sexo e piquete delimitam somente o snapshot atual do overview.
Atividade histórica e insights farm-wide não fazem junção com a custódia atual para aplicar
esses filtros, evitando reescrever fatos após transferências. Como o domínio ainda não possui
categoria pecuária, a categoria tipada disponível continua sendo `UNCLASSIFIED`.

## Atenção, planejador e insights

A atenção combina, com `UNION ALL`, itens manuais `OPEN` e trabalho derivado. As fontes
`MANUAL` e `DERIVED` permanecem distintas e podem coexistir. O preview tem limite de 1 a 10,
usa por padrão cinco itens e mantém a ordenação total da agenda: data operacional, fonte,
tipo e identificador estável. Itens manuais não viram fatos do domínio e concluir um item não
remove uma condição derivada.

Os insights são determinísticos e tipados: cobertura de pesagem, pendências sanitárias,
pipeline reprodutivo, atenção a partos, execução do planejador e atividade do rebanho. A
cobertura usa `BigDecimal` e compara animais ativos elegíveis com animais sem pesagem devida.
Uma fazenda vazia retorna `0,00%`. Não são calculadas taxas de mortalidade, fertilidade ou
concepção, escores clínicos, causalidade, recomendações veterinárias ou previsões.

## Segurança, desempenho e efeitos colaterais

OWNER, ADMIN, MANAGER, OPERATOR e VIEWER podem ler. Tenant e fazenda vêm exclusivamente do
`TenantContext`; todos os predicados incluem os dois recortes quando a tabela possui fazenda.
RLS forçada para `app_api` permanece como defesa complementar. Respostas usam
`Cache-Control: no-store`, parâmetros desconhecidos ou repetidos são rejeitados e UUIDs,
datas, enums e períodos inválidos retornam erro sanitizado.

As consultas agregam domínios em CTEs e usam `FILTER`, `GROUP BY`, `generate_series` e
`UNION ALL`. A série é diária, ordenada e preenchida com zero, inclusive em fazenda vazia.
Os índices de eventos, transferências, pesagens, sanidade, gestações abertas, animais por
status/piquete e agenda já cobrem os predicados; por isso a Phase 10C não adiciona migration.
Todos os endpoints são somente leitura e não persistem animais, eventos, tarefas, pesagens,
tratamentos, gestações, insights ou snapshots.

## Fora de escopo

Exportações PDF, Excel e CSV, relatórios agendados, notificações, e-mail, BI genérico,
dashboards configuráveis, IA, recomendações, previsões, GEDAVE e dashboards financeiro, de
estoque ou contábil permanecem fora desta fase.
