# Fechamento das capacidades gerenciais e reprodutivas

Implementação posterior à matriz BEFORE, em 02/10/2026. Regras no backend, sem job, persistência de idade/faixa, cópia de Flutter ou integração regulatória.

## Relatórios internos

`GET /api/v1/herd/reports/age-sex-period?from&to` retorna abertura no dia anterior a `from`, fechamento até `to` e movimentos inclusivos no período por faixa e sexo. Cada evento é classificado na sua própria data. Inclui cadastros, nascimentos, transferências de entrada/saída, vendas e mortes. CREATED da cria com BORN é excluído do fluxo para evitar duplicação. O relatório inclui células de nascimento desconhecido e total geral.

`ageBandChange` é o residual assinado entre posição e fluxo: fechamento − (abertura + entradas − saídas). Em cadastros coerentes representa migração etária entre células, com soma geral zero. Não é medida de desempenho nem evolução sempre positiva; dados incompletos podem também produzir residual. A semântica `RECORDED_FARM_EVENTS_WITH_CURRENTLY_CORRECTED_PROFILE_AGE_AT_EVENT` declara que sexo e nascimento são o perfil corrigido atual, não um cadastro bitemporal.

`GET /current-age-sex-animals` e `/historical-age-sex-animals`, sob `/api/v1/herd/reports`, oferecem detalhes paginados autorizados. O primeiro usa `referenceDate` e população atualmente ativa; o segundo exige `asOf` e reconstrói custódia por eventos até aquela data. Filtros: `ageBand`, `sex`, `unknownBirthDate`, `page`, `size`. `unknownBirthDate=true` é exclusivo de `ageBand` e inclui nascimento ausente ou posterior à referência. A filtragem usa intervalos de nascimento derivados no backend e LIMIT/OFFSET SQL; o navegador não carrega todo o rebanho.

## Reprodução e acompanhamento após parto

`GET /api/v1/herd/animals/{motherId}/reproductive-intelligence` retorna referência, gestação aberta, atenção ao parto e acompanhamento após parto real. A atenção conserva dias exatos e níveis UPCOMING, DUE_TODAY, OVERDUE (1–6), OVERDUE_ATTENTION (7–13), OVERDUE_EXTENDED (14+). As orientações não inferem nascimento, diagnóstico ou confirmação pelo atraso.

O acompanhamento pós-parto usa exclusivamente o evento CALVED real mais recente por data de ocorrência; inclui parto avulso sem gestação associada. O marco `herd.reproduction.postpartum-review-days`, padrão 45, oferece planejamento de avaliação individual. Não significa aptidão automática para cobertura. Orientações operacionais são restritas à matriz atualmente ativa; fatos anteriores continuam na timeline.

`GET /api/v1/herd/reproduction/calving-preview?serviceOn` deriva previsão usando `ReproductionPolicy` (283 dias de calendário). Retorna o fato de origem, a previsão, o número de dias e a policy. Evita duplicação da regra no Angular.

Pendências de parto incluem `calvingAttention`. Pendências de pesagem incluem `reason` com referenceDate, windowDays reais da instalação, sourceDate, cutoffOn e explicação; não pressupõem universalmente 90 dias.

## Calendário

`GET /api/v1/herd/agenda/daily-summary` aceita `from`, `to`, `source`, `type`, `animalId` e `includeOverdue`. O período obrigatório é limitado a 366 dias inclusivos. Retorna dias com `displayOn`, `count` e `maxLevel` INFO/WARNING/DANGER, além do total. `includeOverdue=true` exige início hoje e agrupa atrasados em hoje, conservando maior nível de atraso. Todos os itens são contados, sem depender da primeira página de 100. Brucelose usa a policy atual e tratamentos efetivos, sem nova fórmula legal em SQL.

Agenda e resumo também projetam `nextDueOn` sanitário explicitamente registrado antes do vencimento, com limite inclusivo no período solicitado. Nos itens futuros `pendingWorkType` é nulo, para não afirmar vacinação/vermifugação vencida; o `kind` VACCINATION/DEWORMING mantém o contexto. No dia da data prevista o item passa ao tipo pendente existente. Retrações excluem o fato das duas projeções. Não há periodicidade automática nem obrigação inferida a partir de aftosa histórica.

## Evidência de testes

Comando focado em ReproductiveIntelligencePolicyTest, ReadReproductiveIntelligenceTest, ReadHerdAgendaDailySummaryTest, ReadHerdPendingReasonTest, HerdReportControllerContractTest, HerdReportRepositoryIntegrationTest, HerdAgendaRepositoryIntegrationTest, ReadHerdPendingWorkBrucellosisTest e ArchitectureTest: **66 testes, zero falhas, zero erros, zero ignorados**. Resultado BUILD SUCCESS em `superset-backend-capabilities-focused.log`, fora dos repositórios. Dois erros de compilação transitórios em lineage/grupos durante trabalho paralelo foram corrigidos antes dessa execução bem-sucedida.

Testes JDBC executam migrations reais em PostgreSQL, verificam tenant/RLS e fazenda, animais vendidos/mortos, datas de ocorrência, exclusão CREATED+BORN, seleção histórica e calendário com 105 itens no mesmo dia. Testes de aplicação verificam níveis temporais, marco configurado 30/45 dias, residual etário negativo preservado, filtros inválidos e janela de pesagem 73 dias.

Um teste adicional de contrato HTTP da inteligência reprodutiva foi adicionado após o foco anterior; sua execução pertence à validação final, ainda não contabilizada nos 66 testes. Suíte completa/verify e revisão independente devem constar do relatório consolidado.
