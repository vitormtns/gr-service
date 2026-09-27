# Auditoria funcional do BovNex legado

Esta matriz compara a intenção funcional do código em `../bovnex2` com o backend do eBov. `PARTIAL` e `MISSING` representam trabalho ainda necessário; este documento não declara paridade concluída. O legado é somente leitura. O estado atual do animal não é uma fotografia histórica, e nenhum quadro abaixo é declarado compatível com uma obrigação oficial vigente.

| Domínio e capacidade | Fonte legada | Equivalente no `gr-service` | Estado | Trabalho ou decisão |
| --- | --- | --- | --- | --- |
| Cadastro, identificação, sexo, nascimento e estado | `lib/models/animal.dart`, `lib/app_state.dart` | `herd` cadastro, correção e ciclo de vida | FULL | Preservar unicidade e versão. |
| Mãe, nascimento e parto | `lib/app_state.dart`, `lib/features/herd/herd_add_event_wizard.dart` | `herd` reprodução, relação materna e eventos | SUPERSEDED | O backend vincula parto, gestação e bezerro de forma transacional. |
| Venda e morte | `lib/models/event.dart`, `lib/app_state.dart` | `herd` ciclo de vida, detalhes estruturados no evento e relatórios | PARTIAL | Motivo, canal, comprador e valor passaram a ser preservados; ainda falta vincular o valor pecuário a lançamento financeiro quando apropriado. A validação PostgreSQL está pendente. |
| Piquete, transferência e movimentação | `lib/app_state.dart`, `lib/features/herd/herd_screen.dart` | `herd` piquetes, movimentações e transferências | SUPERSEDED | Os comandos novos têm escopo, versão e idempotência. |
| Grupos manuais e por regra | `lib/app_state.dart` (`HerdGroup`, `HerdSmartRules`) | `/api/v1/herd/groups`, associação manual e regras dinâmicas por fazenda | FULL | Grupos têm versão, arquivamento e isolamento por tenant/fazenda. Filtros dinâmicos usam estado atual; idade é calculada na data de referência. |
| Fotos locais de animais | `lib/models/animal.dart` | Nenhum contrato de anexo | PARTIAL | Caminhos locais são `CLIENT_ONLY`; referências seguras a objetos, autorização e ciclo de vida de anexos precisam de desenho próprio. |
| Vacinação, vermifugação, produto e próxima aplicação | `lib/models/event.dart`, `lib/services/planner_service.dart` | Tratamentos, pendências, agenda e relatórios | SUPERSEDED | O backend registra `nextDueOn` e não impõe intervalo legal legado. |
| Brucelose | `lib/features/reports/gedave_report_screen.dart` | Código estruturado, política etária e fatos efetivos | SUPERSEDED | A simples presença de texto no legado não prova cumprimento sanitário. |
| Aftosa | `lib/features/reports/reports_screen.dart`, `lib/features/alerts/sanitary_alerts.dart` | Vacinação genérica | PARTIAL | Precisaria de código estruturado e semântica histórica/configurável; não copiar intervalo de 365 dias nem presumir obrigatoriedade atual. |
| Inseminação, cobertura e previsão de parto | `lib/features/herd/herd_add_event_wizard.dart`, `lib/services/planner_service.dart` | Gestação com duração configurável e previsão registrada | SUPERSEDED | O valor padrão de 283 dias pode ser sobrescrito; revisar por espécie/raça quando o produto modelar esses dados. |
| Alertas de parto e conclusão de gestação | `lib/features/herd/services/reproduction_alerts.dart` | Pendências, agenda e estados de gestação | SUPERSEDED | Não repetir alerta após parto/encerramento real. |
| Pesagens e evolução | `lib/models/event.dart`, `lib/services/planner_service.dart` | Pesagens, pendências, relatórios e dashboard | FULL | A agenda é manejo, não obrigação legal. |
| Produção de leite por animal, turno e tendência | `lib/features/milk/milk_insights.dart`, `lib/app_state.dart` | Registros imutáveis, histórico, resumo por animal e indicadores da fazenda | FULL | Tendência usa comparação de 15% com a média dos registros dos últimos sete dias; não é diagnóstico veterinário. |
| Tarefas manuais e automáticas | `lib/models/planned_task.dart`, `lib/services/planner_service.dart` | Planner persistido, vínculo opcional a grupo de manejo e agenda derivada | SUPERSEDED | O vínculo a grupo é validado na fazenda e pode ser filtrado. Prazos de GEDAVE não são fixos. |
| Saldo atual por sexo e faixa etária | `lib/features/reports/gedave_report_screen.dart` | Novo quadro `/reports/current-age-sex-balance` | PARTIAL | Dados atuais agrupados; teste PostgreSQL/RLS ainda precisa passar. Não é declaração GEDAVE. |
| Saldo anterior, nascimentos, mortes, vendas e evolução por período | `lib/app_state.dart` (`generateGedaveReport`) | `/reports/period-reconciliation` reconcilia entradas e saídas registradas por fazenda | PARTIAL | Há saldo inicial e final por eventos, nascimentos, vendas, mortes e transferências. Falta projeção histórica por sexo/faixa etária com correções de perfil preservadas. O legado mistura venda com reserva para abate; não copiar essa inferência. |
| Quadros sanitários e exportação GEDAVE | `lib/features/reports/gedave_report_screen.dart`, `lib/features/reports/reports_screen.dart` | Relatórios sanitários genéricos | PARTIAL | Validar layout, categorias e regras oficiais atuais antes de adapter/exportação específica. PDF montado pelo cliente é `CLIENT_ONLY`. |
| Relatórios de saúde, reprodução, movimento e peso | `lib/features/reports/reports_screen.dart` | `/api/v1/herd/reports/*` | PARTIAL | Conferir filtros e totais detalhados; venda com valor e motivo de morte dependem de fatos estruturados. |
| Receitas, custos e transações gerais | `lib/models/event.dart`, `lib/features/reports/reports_screen.dart` | Módulo `finance` | PARTIAL | Lançamentos financeiros existem, mas não há vínculo explícito com venda pecuária. |
| Sincronização offline e IDs antecipados | `lib/services/sync/supabase_sync_service.dart` | UUIDs e idempotência de comandos | PARTIAL | Fila local e estado da tela são `CLIENT_ONLY`; protocolo de importação/conflito por lote ainda não existe. |
| Notificações locais e navegação | `lib/features/planner/planner_screen.dart`, `lib/features/dashboard/dashboard_screen.dart` | Agenda e pendências como dados | CLIENT_ONLY | Renderização e notificações do dispositivo pertencem ao cliente. |

## Decisões de interpretação

- `AgePolicy` do backend define as faixas. O legado usa o rótulo `36+` para animais acima de 36 meses, enquanto o modelo atual usa `MONTHS_37_PLUS` para meses completos maiores que 36.
- O quadro atual por faixa usa `referenceDate` apenas para a idade. `positionSemantics=CURRENT_STATE_AGED_AT_REFERENCE` informa que a posição do rebanho é a atual. Uma consulta com referência anterior não reconstrói a fazenda naquela data.
- A classificação de aftosa e os prazos de GEDAVE exigem validação regulatória atual. Não há base para publicá-los como conformidade oficial.
- O gerador histórico em `lib/app_state.dart` e o quadro sanitário em `lib/features/reports/gedave_report_screen.dart` usam critérios diferentes de período; a divergência requer um contrato temporal explícito antes de portar a exportação.
- Grupos `SMART` consultam o estado atual dos animais. `referenceDate` altera somente a idade calculada, nunca reconstrói associação, gestação ou estado histórico. A regra legada `onlyPendencies` significa perfil incompleto (nascimento ou mãe ausente); o contrato novo a chama `onlyMissingProfile`. `onlyReproductionActive` consulta uma gestação aberta real, evitando inferir estado atual por um evento antigo de inseminação.
- `/api/v1/herd/reports/period-reconciliation?from=...&to=...` exige datas explícitas e devolve `RECORDED_FARM_EVENT_LEDGER`. O saldo inicial soma entradas (`CREATED`, `BORN`, `TRANSFERRED_IN`) anteriores a `from` e subtrai saídas (`SOLD`, `DECEASED`, `TRANSFERRED_OUT`); o saldo final aplica os movimentos até `to`, inclusive. O relatório reflete fatos atualmente registrados com data de ocorrência, não uma fotografia do que era conhecido à época e não representa declaração GEDAVE.

## Contrato de grupos de manejo

- `POST /api/v1/herd/groups`: `{id,name,kind,rules?}`; `kind` é `MANUAL` ou `SMART`. O mesmo `id` e conteúdo podem ser reenviados sem duplicação. `kind` não muda depois da criação.
- `GET /api/v1/herd/groups` e `GET /api/v1/herd/groups/{id}`: somente grupos ativos.
- `PUT /api/v1/herd/groups/{id}`: substitui nome e regras com `{expectedVersion,name,rules?}`. Regras omitidas equivalem a regras vazias.
- `POST /api/v1/herd/groups/{id}/archive`: arquiva com `{expectedVersion}`.
- `PUT` e `DELETE /api/v1/herd/groups/{id}/animals/{animalId}`: adicionam ou removem um animal de grupo manual com `{expectedVersion}`. Animal e grupo precisam pertencer à mesma fazenda autorizada.
- `GET /api/v1/herd/groups/{id}/animals?page=0&size=20&referenceDate=...`: lista estável e paginada. Para grupos por regra, filtra sexo, estado, idade em meses completos, gestação aberta e perfil incompleto; `positionSemantics=CURRENT_STATE_AGED_AT_REFERENCE`.
- Escrita: `OWNER`, `ADMIN`, `MANAGER`. Leitura: esses papéis, `OPERATOR` e `VIEWER`. Toda operação usa o `TenantContext` autorizado, filtros explícitos e RLS.
- O planejador aceita `groupId` opcional no comando de criação e correção, retorna o vínculo e permite filtrar `GET /api/v1/herd/planner-items?groupId=...`. A referência exige grupo ativo da mesma fazenda. Comandos históricos sem `groupId` preservam a representação canônica anterior para manter replays idempotentes.
