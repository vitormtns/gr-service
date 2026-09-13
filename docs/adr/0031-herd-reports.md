# ADR 0031 — Relatórios pecuários

## Estado

Aceita para a Phase 10B.

## Decisão

A API oferece oito relatórios explícitos sob `/api/v1/herd/reports`: posição do rebanho, ciclo de vida, movimentações, transferências, pesagens, sanidade, reprodução e planejador. Cada contrato possui resumo e itens tipados. Não existe definição dinâmica de relatório, SQL configurável, tabela de reporting, snapshot materializado ou cache persistente.

Os relatórios são modelos de leitura derivados das fontes operacionais existentes. A arquitetura permanece `Controller -> Application/query service -> query port -> JDBC adapter`. O serviço abre a transação tenant-aware e o adaptador usa SQL set-based com predicados explícitos de `tenant_id` e `farm_id`; RLS com `FORCE ROW LEVEL SECURITY` continua sendo a segunda barreira.

## Estado atual e fatos históricos

A posição do rebanho usa a custódia e a localização atuais em `app.animals`. Somente `ACTIVE` participa do total; `SOLD` e `DECEASED` não participam. Uma transferência altera imediatamente a fazenda em que o animal aparece nesse relatório.

Ciclo de vida, movimentações, transferências, pesagens, tratamentos e eventos reprodutivos usam a fazenda imutável em que o fato ocorreu. Transferir o animal posteriormente não remove nem reatribui esses fatos. `BORN` é o fato de nascimento; `CREATED` continua sendo apenas a criação do agregado. Na reprodução, `CALVED` conta partos e `BORN` conta crias, sem somar os dois como se fossem partos distintos.

Referências históricas do animal usam somente `id`, identificação e nome capturados pelo evento `CREATED` da mesma fazenda histórica. Quando esse snapshot não existe no contexto consultado, identificação e nome são omitidos. Os modelos históricos nunca expõem fazenda, piquete ou custódia atuais por uma junção ingênua com `app.animals`.

No relatório de transferências, somente o lado correspondente à fazenda do contexto é exibido. A contraparte permanece sanitizada mesmo quando está no mesmo tenant; consultar uma fazenda não cria autorização transitiva para a outra.

## Filtros, datas e paginação

Todos os itens detalhados são paginados, com tamanho entre 1 e 100, offset protegido contra overflow e desempate estável por UUID. Datas `from` e `to` são inclusivas. Quando ausentes, a janela padrão é de 365 dias, terminando na data fornecida pelo `Clock`; a janela máxima é de 3.650 dias. Ambas podem ser ajustadas por `herd.reports.default-range-days` e `herd.reports.maximum-range-days`.

Parâmetros desconhecidos ou repetidos, UUIDs, datas e enums malformados, caixa não suportada, paginação inválida e intervalos invertidos ou excessivos retornam o contrato sanitizado `HERD_QUERY_INVALID`. OWNER, ADMIN, MANAGER, OPERATOR e VIEWER podem ler; não há mutação na Phase 10B.

O modelo atual de animais não possui categoria pecuária. Para não inventar classificações por sexo ou idade, os relatórios expõem `UNCLASSIFIED`, inclusive como único filtro de categoria aceito onde o contrato prevê categoria. A introdução de categorias reais exige fato de domínio e migration próprios.

## SQL e desempenho

As agregações usam `GROUP BY`, `FILTER`, `count(distinct ...)` e CTEs de snapshots. Resumo e página usam consultas separadas e limitadas para manter o SQL legível; não há carregamento de animais seguido de consultas por animal. Os índices da Phase 10B cobrem eventos por fazenda/tipo/data, transferências por origem ou destino/data, pesagens por fazenda/data e tratamentos por fazenda/tipo/data. Os índices existentes continuam atendendo posição, movimentos, gestações abertas e planejador.

## Fora de escopo

PDF, XLSX, CSV, envio por e-mail, agendamento de relatórios, dashboards, insights, taxas de concepção, ganho médio diário e um mecanismo genérico de relatórios permanecem fora da Phase 10B.
