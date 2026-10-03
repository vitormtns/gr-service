# Inventário independente do comportamento BovNex

Data da leitura: 02/10/2026. Fonte primária: código atual de `bovnex2/lib`, somente leitura. Este inventário registra a matriz BEFORE; não afirma que patches posteriores passaram pelos testes. A classificação considera a intenção útil, separando bugs e promessas sem implementação. Evidências e classificações devem ser cruzadas com a matriz FINAL e com testes observáveis pelo verifier.

## Método e alcance

Cada entrada explicita A (fatos), B (regra), C (fronteiras), D (gatilho temporal), E (informação), F (ação), G (histórico/as-of), H (antecipação), I (escopo), J (vazio/erro). “Atual” significa estado presente; “não” significa ausência daquele comportamento no legado, não ausência obrigatória no sucessor. Escopo padrão legado: dados de AppState da fazenda selecionada, armazenamento local particionado por usuário + token da fazenda. Isso não prova isolamento de servidor; A uthGate utiliza uid como organizationId. Não copiar esse acoplamento.

O mapa inclui caminhos observáveis e caminhos sem consumidor. Classe existente não equivale a capability entregue. Uma ação puramente visual ou texto “em breve” não vira gap obrigatório. Comparação eBov abaixo usa classes reais de aplicação/repositório e superfícies reais do portal; a evidência de testes/commit cabe ao gate FINAL.

## Mapa de todos os arquivos de lib

| Arquivos | Papel real |
|---|---|
| `main.dart`, `app.dart` | Inicialização Supabase, Provider, tema e erro de inicialização; sem regras pecuárias adicionais. |
| `auth_gate.dart`, `login/login_screen.dart`, `services/auth_service.dart`, `services/supabase_client.dart`, `services/profile_service.dart`, `services/org_bootstrap_service.dart` | Sessão, formulário e bootstrap. ProfileService/OrgBootstrapService não são evidência de um fluxo de gestão completo. |
| `routing/app_router.dart` | Dashboard, Rebanho, Agenda, Relatórios, Configurações; lateral desktop e navegação inferior móvel. Busca global é explicitamente fake. |
| `app_state.dart` | CRUD, grupos, validações, parto composto, outbox/sync, tarefas e relatório histórico. Fonte central de regras. |
| `models/animal.dart`, `models/event.dart`, `models/event_types.dart`, `models/farm_meta.dart`, `models/planned_task.dart`, `models/farm_plan.dart` | Fatos, metadados e tarefa. FarmPlan é modelo sem fluxo ativo; foto é campo de Animal sem captura/exibição em lib. |
| `services/farm_storage.dart`, `services/local_json_farm_storage.dart`, `services/sync/supabase_sync_service.dart` | Persistência local, outbox, confirmação, tentativa e pull remoto; mecanismo cliente. |
| `services/planner_service.dart` | Deriva recorrência vacina180/vermi120/peso90 e GEDAVE180; NÃO possui consumidor em lib. Registrar como código não entregue, sem restaurar regras. |
| `features/dashboard/dashboard_screen.dart` | Comando diário, resumo, leite, sanidade, reprodução, composição e transição etária antecipada; hub navega ao ReportsScreen. |
| `features/alerts/sanitary_alerts.dart` | Alertas sanitários, totais, severidade, filtros e resumo rápido do animal. |
| `features/milk/milk_insights.dart` | KPIs reais e tendência por registro. |
| `features/herd/herd_screen.dart` | Exploração, busca/filtros, grupos, seleção, cadastro rápido, eventos, perfil contextual e prioridades reprodutivas. |
| `features/herd/herd_add_animal_page.dart`, `herd_add_event_page.dart`, `herd_add_event_wizard.dart`, `herd_import_page.dart` | Formulários, calendário manual e importação com prévia. |
| `features/herd/services/reproduction_alerts.dart` | Severidade temporal de parto. |
| `features/herd/widgets/animal_detail_panel.dart`, `animal_detail_screen_full.dart`, `animal_profile_action.dart` | Perfil, linhagem, timeline, leite, contexto reprodutivo, registro parto. Enum duplicado não cria nova capability. |
| `features/herd/widgets/bulk_event_sheet.dart`, `bov_animal_card.dart`, `resumo_card.dart`, `status_filter_chip.dart`, `herd_style.dart` | Eventos em lote e apresentação/filtros derivados. |
| `features/planner/planner_screen.dart` | Agenda observável baseada em tarefas manuais + previsões de parto. |
| `features/reports/reports_screen.dart` | Relatórios ativos: faixa, mortes, vendas, vacinas, GEDAVE e PDF. |
| `features/reports/gedave_report_screen.dart` | Implementação paralela sem rota/consumidor. Não é prova de capability adicional entregue. |
| `features/settings/settings_screen.dart` | Fazendas, metadados, aftosa, importação, sessão; backup apenas promessa. |
| `core/pt_plural.dart`, `core/enums.dart`, `core/constants.dart`, `core/date_utils.dart` | Utilitários/constantes; date_utils não contém regra reutilizável de calendário nesta versão. |
| `theme/app_theme.dart`, `bovnex_theme.dart`, `bov_tokens.dart`, `theme/widgets/bov_badge.dart`, `bov_button.dart`, `bov_card.dart`, `bov_list_tile.dart`, `bov_section.dart` | Aparência e componentes; não criar equivalência de negócio por cópia visual. |

## Matriz comportamental BEFORE

### Identidade, fazenda e operações

**L01 — Entrar/sair e bloquear navegação sem sessão.** Evidência: `auth_gate.dart:25`, `login/login_screen.dart:73`, `services/auth_service.dart:12`, `settings_screen.dart:708`. A email/senha/sessão; B signIn/signOut; C campos/formulário; D mudança sessão; E loading/erro; F entrar/sair; G não; H não; I usuário; J erro exibido. **FULL_PARITY**: sessão e guardas reais do portal (`gr-web/src/app/core/auth`, rotas protegidas); validar novamente com contexto tenant.

**L02 — Selecionar e persistir fazenda.** `app_state.dart:117, 165, 266`, `local_json_farm_storage.dart:19-44`, `settings_screen.dart:185`. A token/fazendas; B recarrega listas separadas; C token atual; D troca; E nome/dados fazenda; F selecionar; G não; H não; I usuário/fazenda; J loading. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: TenantContext, memberships, fazendas e seletor de contexto do portal preservam intenção com autorização/RLS; token local não é autoridade.

**L03 — Criar animal e cadastrar vários rapidamente.** `app_state.dart:779`, `herd_screen.dart:612`. A identificação/sexo/nascimento/mãe; B valida/cria; C nascimento não futuro; D comando; E sucesso/novo animal; F cadastrar; G histórico não append-only; H não; I fazenda; J validação. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: CreateCurrentFarmAnimal/ImportCurrentFarmAnimals e páginas de cadastro/importação preservam resultado com idempotência e fatos auditáveis.

**L04 — Editar fatos cadastrais.** `app_state.dart:822`. A perfil novo; B valida/substitui; C mesmo id; D comando; E perfil atualizado; F editar; G sobrescreve; H não; I animal/fazenda; J erro. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: CorrectCurrentFarmAnimal e correção de mãe registram alterações em eventos.

**L05 — Excluir animal apagando eventos e desfazendo linhagem.** `app_state.dart:847-895`. A id; B hard delete em cascata local; C todos eventos; D comando; E desaparecimento; F excluir; G destrói histórico; H não; I animal/fazenda; J sem preservação. **OBSOLETE_OR_UNSAFE**: não restaurar eliminação destrutiva de fatos em arquitetura append-only.

**L06 — Validar mãe coerente.** `app_state.dart:650-720`. A sexo/nascimento/mãe/saída; B mãe existente/fêmea, não própria, não mais nova e idade mínima18; C igualdade nascimento/saída permitida; D salvar; E mensagem específica; F corrigir; G usa saída real; H não; I animal/mãe na fazenda; J rejeita ausência. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: CorrectCurrentFarmAnimalMother e validações atuais devem preservar coerência e tratar política etária como domínio, não copiar18 sem contrato.

**L07 — Buscar por identificação/id e filtrar sexo/status.** `herd_screen.dart:148-205`. A texto/sexo/status; B substring case-insensitive + filtros; C ativo/vendido/morto; D digitação; E lista/contador; F abrir animal/limpar filtro; G atual; H não; I fazenda; J vazio filtrado. **FULL_PARITY**: HerdAnimalQuery/JdbcHerdAnimalQueryRepository e animal-list-page.

**L08 — Categorias contextuais de exploração.** `herd_screen.dart:159-185, 2851`. A sexo/meses; B fêmea<12 bezerra, 12..23 novilha,>=24 matriz; macho reprodutor sem idade; C 12/24; D leitura atual; E contagem e filtro clicável; F explorar categoria; G atual; H não; I ativos; J sem nascimento tratado como0. **OBSOLETE_OR_UNSAFE** para inferir aptidão/reprodutor apenas por sexo/idade; intenção de exploração por sexo/idade deve ser preservada pelas faixas/agrupamento do eBov, sem categoria persistida nem animal sem data como recém-nascido.

**L09 — Evento individual com dados tipados.** `models/event.dart:3-43`, `app_state.dart:897-1008`. A tipo/data/peso/valor/vacina/motivo/canal/comprador/leite/notas; B valida/adiciona; C data <=hoje e >=nascimento, ativo, sexoF para reprodução/leite; D comando; E timeline; F registrar; G eventos; H previsão somente inseminação; I animal/fazenda; J validação. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: comandos tipados de manejo/lifecycle/reprodução/leite, versão e operação idempotente; não restabelecer evento genérico que contorna salvaguardas.

**L10 — Morte e venda mudam estado e preservam dados comerciais.** `app_state.dart:750-765, 988`, `event.dart:17-21`. A saída, data, valor/canal/comprador/motivo; B muda status; C não permite segunda saída; D comando; E status/timeline; F registrar; G data de ocorrência; H não; I animal; J rejeita já morto/vendido. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: LifecycleCurrentFarmAnimal, LifecycleEventDetails e relatórios completos.

**L11 — Apagar evento da timeline.** `app_state.dart:1010`, `animal_detail_panel.dart:1654`. A evento; B remove; C qualquer selecionado; D comando; E desaparecimento; F excluir; G destrói fato sem recomputar status; H não; I animal; J sem auditoria. **OBSOLETE_OR_UNSAFE**: retração sanitária e fatos efetivos atuais preservam correção com trilha; não apagar histórico.

**L12 — Eventos em massa com validação prévia.** `app_state.dart:1026-1098`, `bulk_event_sheet.dart`. A seleção/tipo/data/dados; B valida todos antes de inserir; C seleção vazia retorna; D comando; E contador/sucesso; F aplicar lote; G eventos individuais; H não; I seleção fazenda/grupo; J mensagem de erro. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: batch de saúde/pesagem/cobertura + animal-batch-operations com operationId/version; todo comando deve ser atomicamente coerente.

**L13 — Importar tabela com prévia.** `herd_import_page.dart:36-164, 165-229`. A texto CSV; B separador;/,, header, prévia e duas fases animais/mães; C nascimento dd/MM/aaaa; D analisar/importar; E linhas e total; F colar/exemplo/analisar/importar; G não; H não; I fazenda; J vazio/erro. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: ImportCurrentFarmAnimals e formulário web; validação estrita substitui sexo inválido=>M e data inválida silenciosamente ignorada.

### Grupos, perfil e inteligência etária

**L14 — Grupos manuais, membros e grupo ativo.** `app_state.dart:476-593`. A nome/ids; B resolver membros, adicionar/remover; C id membro existente; D comando/troca grupo; E conjunto/contador; F manejar grupo; G atual; H não; I fazenda; J grupo ausente=>lista vazia. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: HerdGroupService, JdbcHerdGroupRepository, groups-page, arquivo com preservação histórica.

**L15 — Grupo inteligente por sexo/status/idade.** `app_state.dart:594-628, 1567`. A filtros, min/max; B deriva membros no momento; C min/max inclusivos; D leitura atual; E membros; F criar/editar/explorar; G sem asOf; H muda membros com idade; I fazenda; J nascimento ausente=>0 no legado. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: HerdGroup.Rules e queries server-side preservam filtros com AgePolicy; ausência de nascimento precisa permanecer explicitamente desconhecida.

**L16 — Grupo/atenção de cadastro incompleto e reprodução ativa.** `app_state.dart:608-625`, `herd_screen.dart:425`. A ausência nascimento/mãe ou inseminação com previsão; B seleciona; C OR de campos ausentes; D leitura; E contador/lista; F revisar perfil; G atual; H não; I fazenda; J sem integrantes. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: regras onlyMissingProfile/onlyReproductionActive e reprodução possível/confirmada; não equiparar todas fêmeas a maternidade obrigatória.

**L17 — Perfil, identificação e idade amigável.** `bov_animal_card.dart:216-240`, `animal_detail_panel.dart:273`. A birthDate/sexo/status; B meses completos e dias para jovem; C aniversário mensal; D leitura; E idade amigável; F abrir perfil; G atual; H não; I animal; J sem idade. **IMPLEMENT_NOW** até provar que perfil/lista recebem derivação de backend: rótulo de nascimento sozinho não preserva informação de idade/faixa; Angular não deve duplicar AgePolicy.

**L18 — Faixas 0–2/3–8/9–12/13–24/25–36/37+.** `dashboard_screen.dart:460-477`, `reports_screen.dart:32-48`, `app_state.dart:1411-1427`. A nascimento/referência; B meses calendário dia<dia subtrai1; C 3/9/13/25/37; D leitura; E faixa; F explorar; G asOf no relatório; H próxima faixa separada; I animal/fazenda; J data ausente omitida. **FULL_PARITY** de regra: `AgePolicy.java:12-60`, AgeBand; apresentação depende L17/L20.

**L19 — Composição por faixa e lista clicável.** `dashboard_screen.dart:1139-1167, 620`, `reports_screen.dart:54-73, 180-258`. A ativos/nascimento; B agrupa/conta; C todas6 faixas mesmo vazias; D leitura; E totais/lista/sexo/data; F abrir detalhe; G atual; H não; I fazenda; J “Nenhum animal nesta faixa”. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: ReadHerdReports.currentAgeSexBalance/historicalAgeSexBalance e herd-statements-page preservam composição, sexo e contagem desconhecida; ação contextual de exploração deve ser conferida no patch.

**L20 — Antecipação de transição etária em15 dias.** `dashboard_screen.dart:1169-1218, 4421-4510`. A nascimento/hoje; B próxima fronteira3/9/13/25/37; C intervalo [now, now+15] porém hora exclui transição hoje às00:00; D passagem tempo; E total, até3 animais, de/para, data,+restantes; F sem link neste card; G não; H 15 dias; I ativos fazenda; J mensagem explícita quando zero. **IMPLEMENT_NOW**: AgePolicy.nextTransitionDate não era consumida operacionalmente na baseline; precisa projeção server-side + Home/lista/perfil. Corrigir rollover month-end e hora, não copiar bug.

**L21 — Linhagem navegável por múltiplas gerações.** `animal_detail_panel.dart:75, 612-891, 1090`, especialmente `720-744`. A motherId e população; B percorre mães/avós e descendentes BFS; C interrompe mãe ausente, BFS deduplica descendentes, ancestrais não têm proteção de ciclo; D abrir; E ancestrais, descendentes, identificação, sexo e idade; F abrir qualquer animal relacionado; G perfil atual; H não; I animal/fazenda; J relação ausente interrompe percurso. **IMPLEMENT_NOW**: perfil direto mãe/crias do eBov não preserva travessia de avós/netos. Implementar leitura autoritativa autorizada, limitada, paginada e sem N+1; não copiar travessia sem limite/ciclo nem conceder acesso transitivo entre fazendas.

**L22 — Timeline com detalhes e contadores de eventos.** `animal_detail_panel.dart:78-89, 445-476, 1559-1660`, `animal_detail_screen_full.dart:16`. A eventos do animal; B ordem recente e contagem tipos; C todos registrados; D leitura; E peso/valor/vacina/notas/data; F novo evento; G histórico; H não; I animal; J nenhum evento. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: GetCurrentFarmAnimalHistory, eventos tipados append-only e perfil paginado.

### Reprodução

**L23 — Serviço e previsão de parto em283 dias.** `app_state.dart:1260-1288`, `herd_screen.dart:604`. A fêmea, data, semenIdentifier; B date+283; C calendário por dias, evento não futuro; D registrar; E serviço/previsão; F inseminar; G serviço; H parto futuro; I matriz; J macho rejeitado. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: ReproductionPolicy.GESTATION_DAYS283 e ManageHerdReproduction preservam estimativa com serviço natural/inseminação, gestação POSSIBLE/CONFIRMED explícita.

**L24 — Último serviço e encerramento por parto real.** `animal_detail_panel.dart:90-101, 163-201`. A serviço/parto; B último evento e parto posterior encerra; C comparação estrita; D leitura; E estado/datas; F registrar parto; G eventos; H previsão; I matriz; J sem serviço. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: gestação identificada, diagnóstico/terminação/CALVED e timeline evitam misturar gestações distintas.

**L25 — Alerta de parto próximo.** `reproduction_alerts.dart:84-150`. A previsão/hoje/última inseminação; B diffDays; C -7..-1; D dia atual; E animal, data, dias, mensagem; F perfil/parto; G não; H 7 dias; I ativo; J não encontrado/inativo/longe ignorado. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: CALVING_UPCOMING, ReadHerdPendingWork, agenda, dashboard e reproduction-overview preservam previsão e dias com horizonte contratual.

**L26 — Atraso granular0..6/7..13/>=14.** `reproduction_alerts.dart:153-169`, não confiar comentários1–7/7–14/>14. A previsão/reference; B dias decorridos, severidade; C 0 já atraso no legado; D diário; E daysDiff e gradação; F observar/registrar; G não; H próximo separadamente; I matriz ativa; J sem previsão omitido. **SUPERSEDED_BY_BETTER_EBOV_MODEL** para informação quantitativa: pending.daysOverdue e reproduction-overview:31 exibem grau real; não exigir rótulo médico crítico sem contrato. Testar igualdade esperada, 7, 14 e sorting.

**L27 — Mensagem presume parto ocorrido apenas por atraso.** `reproduction_alerts.dart:66-72`. A previsão antiga; B “quase certo” de nascimento; C>=14; D atraso; E certeza fisiológica não suportada; F cadastrar bezerro; G não; H não; I matriz; J não considera diagnóstico. **OBSOLETE_OR_UNSAFE**: não deduzir CALVED/BORN de calendário nem sugerir fato inexistente.

**L28 — Badges e prioridade reprodutiva na lista.** `herd_screen.dart:220-423`, `bov_animal_card.dart:9, 421`. A previsão/sexo/status; B <=30 parto próximo, depois previsão como proxy pós-parto; C 30/45/365; D leitura; E badge; F foco partos; G atual; H 30 dias; I ativosF; J sem previsão. **SUPERSEDED_BY_BETTER_EBOV_MODEL** na intenção de atenção por contagem e prazo: reproduction-overview e pending list apresentam previsão, dias e vínculo real. Proxy pós-parto é separado em L29.

**L29 — Pós-parto presumido a partir de expectedBirthDate.** `herd_screen.dart:267-293, 385`. A previsão; B trata data passada como parto e pronto aos45; C 45..365; D diário; E “pós-parto”/“pronta”; F cobertura; G não fato; H readiness; I matriz; J sem parto não distingue. **OBSOLETE_OR_UNSAFE**: previsão não prova nascimento nem elegibilidade reprodutiva.

**L30 — Dias desde parto REAL e antecipação pós-parto.** `animal_detail_panel.dart:347-350, 1438-1468`. A último evento parto/hoje; B dias desde o parto; C<45/==45/>45; D diário; E dias desde parto e até/depois próxima cobertura; F planejar; G parto real; H contagem regressiva; I animalF; J sem parto oculta. **IMPLEMENT_NOW** para dias desde CALVED e orientação de planejamento explicável. Regra universal “pronta para cobertura45 dias” é **separadamente L31**; não esconder intenção útil por causa do texto inseguro.

**L31 — Aptidão universal45 dias após parto.** `features/herd/widgets/animal_detail_panel.dart:1456-1468` (_PostPartoHint). A parto+tempo sem avaliação; B “pronta”; C 45; D diário; E afirma aptidão; F nova cobertura; G usa parto; H 45 fixo; I qualquer fêmea; J não considera saúde/protocolo. **OBSOLETE_OR_UNSAFE**: derivar aptidão clínica de prazo universal é regressão; eventual protocolo configurado deve ser advisory com fatos reais.

**L32 — Parto composto: cria bezerro, mãe, nascimento e encerra gestação.** `app_state.dart:1119-1258`, `animal_detail_panel.dart:1892`. A matriz, data, identificação/sexo cria; B bundle; C requer serviço e valida parentesco; D confirmar; E sucesso; F registrar parto; G eventos + serviço sobrescrito; H remove alerta; I mãe/cria/fazenda; J erro retornado. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: ManageHerdReproduction calving transacional e fatos BORN/CALVED/gestação, sem limpar história.

### Sanidade e pesagem

**L33 — Brucelose: elegíveis fêmeas3–8 inclusive.** `sanitary_alerts.dart:245-300`, `dashboard_screen.dart:1082`, `reports_screen.dart:681`. A sexo, nascimento, vacinas; B janela e registro; C 3/8/9; D idade atual; E alvo/vacinadas/pendentes e animal; F revisar/registrar; G histórico registro; H fechamento janela; I ativosF; J nascimento ausente excluído. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: BrucellosisPolicy/BrucellosisPrimaryCompliance e health surfaces respeitam espécie, produto, janela na aplicação e fatos efetivos.

**L34 — Brucelose: intensidade e fora da janela.** `sanitary_alerts.dart:285-330`. A nascimento/registro; B 3..6 warning, 7..8 critical,>8 sem registro warning; C 6/7/8/9; D diário; E pendência/janela passada; F verificar; G não distingue vacina inválida; H risco janela; I ativosF; J nenhum registro. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: BRUCELLOSIS_DUE/WINDOW_MISSED e leitura primária preservam urgência de janela sem alegar regularização por registro tardio.

**L35 — Aftosa anual como obrigação atual.** `sanitary_alerts.dart:159-239`, `dashboard_screen.dart:1114`, `FarmMeta.enableAftosa`. A última vacina/idade; B 365 dias/30 antes; C>=365 vence,<=30 avisa, sem vacina>=3 crítico; D diário; E vencida/em dia; F vacina imediatamente; G histórico; H 30 dias; I ativos; J nunca vacinado. **OBSOLETE_OR_UNSAFE**: regra proibida pela missão; não restaurar obrigatoriedade nem flag que reativa obrigação histórica.

**L36 — Vermifugação fixa sem registro/60/90.** `sanitary_alerts.dart:336-393`. A último evento; B falta warning,>60 warning,>90 critical; C 60 ainda normal/90 warning; D diário; E último registro e recomendação; F verificar/registrar; G eventos; H após60; I ativos; J nunca registrado. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: nextDueOn/protocolo configurado, DEWORMING_DUE e planner preservam manejo; nunca apresentar90 como lei ou necessidade clínica universal.

**L37 — Sanidade ordenada, contagem e filtro.** `sanitary_alerts.dart:65-139, 621`. A alertas; B crítico/aviso/normal e data; C okCount=ativos sem qualquer issue; D leitura; E totais, categoria, badges, ref; F filtros/resumo animal; G últimos5 eventos; H por regra; I fazenda; J vazio explícito. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: health-command-center, pending, dashboard e perfil oferecem current facts + histórico com salvas reais. Não equiparar “sem alerta” a conformidade completa.

**L38 — Pesagem e histórico.** `event.dart:15`, `animal_detail_panel.dart:1559`, `app_state.dart:1026`. A peso/data; B registra e conta; C regras evento; D comando; E kg/timeline/contador; F individual/lote; G histórico; H não no fluxo ativo; I animal/lote; J sem dado. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: ManageHerdIntelligence.weights, batch, histórico e relatório min/média/max; unidades/precisão explícitas.

### Leite

**L39 — Registro de leite e turno.** `app_state.dart:768-777, 933`, `event.dart:22`. A litros>0, turno opcional, data; B fêmea ativa; C manhã/tarde/noite; D comando; E timeline/último; F registrar; G eventos; H não; I animal; J inválido rejeita. **FULL_PARITY**: MilkService, MilkSession e animal-management.

**L40 — KPIs de leite no dia e média por registro7 dias.** `milk_insights.dart:40-82`, `dashboard_screen.dart:2806`. A eventos leite, ref; B soma litros, distinct animal, média por evento; C[ref-6, ref] inclusivo; D leitura; E litros/fêmeas/média; F perfil/relatórios; G now injetável; H não; I fazenda; J zero/null. **FULL_PARITY**: MilkService.overview e herd-statements-page:220-239; não chamar média de produção diária.

**L41 — Último leite individual, média e tendência±15%.** `milk_insights.dart:84-173`, `animal_detail_panel.dart:374-431`. A último registro/janela7; B delta = (último-média)/média; C>=2 registros, média>0,<=-0.15 queda,>=0.15 alta; D leitura; Eúltimo/data/turno, média, tendência; F registrar; G ref injetável; H não; I animalF; JINSUFFICIENT_DATA. **FULL_PARITY**: MilkTrendPolicy, MilkService.summary e animal-management. Histórico asOf não deve escolher registro posterior à referência.

### Agenda e comando diário

**L42 — Tarefa manual, edição, conclusão e vínculo.** `app_state.dart:1293-1369`, `planned_task.dart:3-21`, `planner_screen.dart:1015`. A título, data, tipo, animal/grupo opcional; B CRUD/completed; C stripTime; D dia escolhido; E tarefa/status; F criar/editar/concluir/excluir; G criação mas edição substitui; H agendamento; I fazenda/animal/grupo; J vazio. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: HerdPlannerService/HerdPlannerOperation e planner/agenda pages; cancelamento preserva histórico. Vínculo a grupo como escopo operacional pode ser resolvido por lote sem atribuir execução a todos implicitamente.

**L43 — Agenda mostra partos/tarefas por dia e atrasados.** `planner_screen.dart:740-860`. A previsões/tarefas; B classifica data<hoje/==/>, marca calendário e soma; C dia sem hora; D selectedDay/hoje; E contagem/marcador/severidade/itens; F abrir perfil/registrar parto; G parto após serviço resolve; H futuro todo; I fazenda; J estado vazio/ações. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: ReadHerdAgenda e reprodução/planner atuais combinam fontes sem inventar execução.

**L44 — Jornada operacional com CTAs e contagem.** `dashboard_screen.dart:727-934`. A alertas, cadastro, eventosHoje; B urgente/recomendado/evolutivo, rotação determinística; C 3recomendados+1evolutivo; D dia; E atenção/progresso; F cadastro, lote, saúde, grupos, relatórios, agenda; G local reset diário; H partos; I fazenda; J calm mode. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: Home operacional e operational-urgency-board preservam ações contextuais/dados verificáveis; não exigir rotação/progresso fictício como regra de negócio.

**L45 — CTA contextual único abre animal; vários abrem foco.** `dashboard_screen.dart:781-801`, `herd_screen.dart:696-745`. A total/id; B um=>perfil, muitos=>filtro; C count1; D clique; E animal/grupo afetado; F registrar; G não; H alertas; I fazenda; J alvo sumiu=>filtro. **FULL_PARITY**: pending-navigation e detalhe reprodução/identidade animal; autorização/contexto devem continuar validados.

**L46 — Resumo de movimentos30 dias.** `dashboard_screen.dart:1057-1080`. A eventos/time; B morte/venda/vacina nos30; C >(now-30),<(now+1); D leitura; E contagens; F relatórios; G janela; H não; I fazenda; J zero. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: ReadHerdDashboard/ReadHerdReports possuem períodos explícitos e contagens completas; não copiar janela com horário ambíguo.

**L47 — Taxa de mortalidade improvisada.** `dashboard_screen.dart:1073-1080`. A ativos+mortes30; B mortes/(ativos+mortes); C denom>0; D leitura; E%; F relatório; G janela; H não; I fazenda; J null. **OBSOLETE_OR_UNSAFE**: denominador não reconstrói população exposta com entradas/saídas/transferências; preferir contagens reais.

### Relatórios e histórico

**L48 — Mortes por motivo com lista.** `reports_screen.dart:264-403`. A eventos morte; B agrupa motivos; C todos eventos; D leitura; E motivo/total, data, animal, notas; F consulta; G histórico; H não; I fazenda; J sem morte. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: HerdReportRepository.LifecycleSummary.deathsByReason/LifecycleItem, JdbcHerdReportRepository e reports-page:54-57 preservam intenção com período, filtro e resumo global.

**L49 — Vendas por canal/comprador/valor e média.** `reports_screen.dart:405-628`. A vendas/valor/canal/comprador; B soma e média sobre todas vendas; C sem valor conta denominador; D leitura; E total, média, detalhes; F consulta; G histórico; H não; I fazenda; J zero. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: LifecycleSummary totalSaleAmount/averageSaleAmount/salesWithAmount/salesByChannel; média apenas valores informados com denominador explícito; não equacionar registro a receita liquidada.

**L50 — Vacinas por faixa/sexo e registrado/pendente.** `reports_screen.dart:631-745`. A ativos/vacinas/birthDate; B elegibilidade brucelose e histórico; C 3..8; D hoje; E listas e totais; F consulta; G atual; H não; I fazenda; J vazio. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: BrucellosisPrimaryCompliance + currentProcedureCoverage e herd-statements-page oferecem leitura segura de fatos; Aftosa anual em L35 é excluída.

**L51 — População histórica por sexo/faixa.** `app_state.dart:1403-1498`. A nascimento/saídas/ref; B vivo emref e classify emref; C nascimento<=ref, saída no início não exclui, no final exclui; D consulta período; E saldo anterior/atual6x2; F datas; G asOf explícito; H não; I fazenda; J sem nascimento omitido. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: historicalAgeSexBalance, historicalAgeSexCounts e herd-statements histórico preservam propósito incluindo transferências; perfil corrigido atual deve ser explicado, sem alegar bitemporalidade.

**L52 — Movimentos entre saldos.** `app_state.dart:1500-1549`, `reports_screen.dart:2616-2650`. A eventos/nascimento/período; B nascimentos, mortes, vendas por faixa/sexo; C[from, to]eventos, UI substitui births por birthDate>ref<=to sóativos; D consulta; E saldos/movimentos; F datas; G histórico parcial; H não; I fazenda; J zeros. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: periodReconciliation/eventLedger inclui CREATED/BORN/SOLD/DECEASED/TRANSFERRED_IN/OUT sem omitir nascido depois vendido. Testar antes/dentro/depois e referência.

**L53 — Evolução artificial em células regulatórias.** `app_state.dart:1531-1540`, `reports_screen.dart:2654`. A diferenças saldo; B clamp negativo0, células bloqueadas; C por faixa; D relatório; E “evolução”; F consulta; G histórico; H não; I fazenda; J zero forçado. **OBSOLETE_OR_UNSAFE**: não copiar fórmula que mascara divergência nem células “impossíveis” regulatórias presumidas; sucessor deve exibir reconciliabilidade dos fatos reais.

**L54 — PDF do relatório consultado.** `reports_screen.dart:906-942`. A posição/idade/sexo/brucelose/datas; B cria PDF via pw/Printing; C dados apresentados; D botão; E documento compartilhável; F exportar; G referência selecionada; H não; I fazenda; J erro biblioteca. **IMPLEMENT_NOW** para relatório INTERNO exportável/imprimível: portal baseline não possui print/CSV/PDF nos relatórios. Não é obrigação de reproduzir declaração oficial; impressão local com linguagem interna é equivalente útil.

**L55 — GEDAVE como declaração/exportação oficial.** `reports_screen.dart:866`, `settings_screen.dart:31`. A datas/metadado; B relatório nomeado; C regras históricas presumidas; D consulta; E GEDAVE; F PDF; G período; H 180 só serviço morto; I fazenda; J sem lastSync escolhe data. **REGULATORY_BLOCKED**: nenhum arquivo aceito, envio/compliance oficial deve ser alegado. Read models úteis são L51/L52/L54; exceção regulatória não exclui esses.

### Cliente e código não entregue

**L56 — Offline local/outbox, contador e sincronização manual.** `local_json_farm_storage.dart:75, 255-350`, `app_state.dart:288-412`, `dashboard_screen.dart:86-459`, `supabase_sync_service.dart:14, 76, 356`. A dados locais, deviceId, ops, receipts; B persistir/enfileirar/retry/push/pull; C uid+farm; D comando/solicitação sync; E pendentes/último sync/erro; F sincronizar; G estado local; H não; I cliente/usuário/fazenda; J erro/backoff. **CLIENT_ONLY_FUTURE_APP**: backlog móvel de operações offline idempotentes, isolamento, conflitos e visibilidade de falha; não tocar gr-app.

**L57 — Promessa de backup e fotos sem fluxo.** `settings_screen.dart:586-610`, `animal.dart:14`, busca photoPaths em lib. A somente campo/texto; B nenhuma ação; C não; D não; E “Em breve”; F nenhuma; G não; H não; I modelo; J não. **OBSOLETE_OR_UNSAFE** enquanto promessa/campo sem comportamento entregue: não declarar capability perdida nem preencher com operação inventada.

**L58 — Recorrências PlannerService sem consumidor.** `planner_service.dart:49-69, 72-345`; nenhum import/instância em lib. A eventos/meta; B vacina180, vermi120, peso90, GEDAVE180; C birthresolve[-20,+30]; D referência; E DTO não utilizado; F CTA sem fluxo; G não; H recorrência; I fazenda; J ignora sem registro. **OBSOLETE_OR_UNSAFE** como requisito de paridade obrigatório: código morto não prova superfície entregue e prazos não têm contrato regulatório. Intenção futura de manejo segue nextDueOn/planner atual.

**L59 — Implementação GEDAVE alternativa sem rota.** `gedave_report_screen.dart:217`; A ppRouter somente ReportsScreen; busca classe só declaração. A dados atuais; B gerador alternativo; C usa now e status atuais; D não roteado; E UI inacessível; F não fluxo; G asOf inconsistente; H não; I fazenda; J não observável. **OBSOLETE_OR_UNSAFE** como variante a copiar: usar intenção interna dos caminhos ativos L51/L52.

**L60 — Busca global fake e marketing sem ação.** `app_router.dart:355`, `login_screen.dart:840`. A texto; B não há busca/noop; C não; D clique; E aparência; F sem efeito; G não; H não; I UI; J sem resultado. **OBSOLETE_OR_UNSAFE**: não contar aparência/promessa como capability útil entregue.

**L61 — Criar grupo manual com conjunto selecionado.** `herd_screen.dart:4459-4490`, `app_state.dart:482`. A nome e pickedIds; B cria grupo e associa seleção numa ação; C exige ao menos um animal; D confirmar; E nome e total criado; F selecionar e criar; G associação atual; H não; I fazenda/seleção; J cancelar ou vazio não cria. **IMPLEMENT_NOW**: HerdGroupService/portal baseline criam grupo e associam individualmente; preservar operação concreta com membros iniciais, autorização e idempotência. Métodos addAnimalsToGroup/removeAnimalsFromGroup sem consumidor não comprovam gestão em lote posterior entregue.

**L62 — Selecionar lista filtrada/grupo como população de lote.** `herd_screen.dart:1703-1839, 2022, 2249, 2361-2390`. A busca, status, categoria, grupo e animalIds; B resolve filtro e seleciona explicitamente todos exibidos; C inseminação só fêmeas ativas; D ação seleciona/confirmar; E total e lista de selecionados; F saúde/peso/inseminação; G snapshot no ato da seleção; H não; I conjunto fazenda; J vazio desabilita/seleção pode ser limpa. **IMPLEMENT_NOW**: animal-list-page mantém seleção para movimento/pesagem/transferência, mas saúde/reprodução baseline pedem seleção novamente e não transportam grupo/população filtrada. Preservar snapshot revisado, limites/paginação explícitos e revalidar versão; não ampliar para todo rebanho silenciosamente.

**L63 — Agregado diário completo com maior severidade.** `planner_screen.dart:800-859`. A partos e tarefas completas; B agrupa data, soma count e maior rank; C futuro=info/hoje=warning/atrasado=danger; D leitura calendário; E marcador e total por dia; F abrir dia/detalhes; G datas originais; H dias futuros; I fazenda; J nenhum item=>nenhum marcador. **IMPLEMENT_NOW**: calendário atual agrupa páginas de detalhes, não população inteira. Criar agregado server-side count/maior estado neutro com mesmos filtros/escopo/fatos efetivos do detalhe; não atribuir gravidade clínica nova.

**L64 — Resolução diária da recomendação após fato registrado.** `app_state.dart:223-264, 815, 1089`, `dashboard_screen.dart:889-921`. A recomendação de cadastro/lote, data e fato concluído; B marca id resolvido e oculta recomendação; C reset na virada do dia; D sucesso de command; E restantes/progresso; F registrar animal/lote; G marca local não histórica; H não; I estado AppState; J sem fato recomenda. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: atividade registrada resolve sua fonte operacional, planner usa estados explícitos e Home lê pendências reais; conclusão de tarefa manual não resolve automaticamente saúde/reprodução. APIs públicas de dismissal manual não têm consumidor em lib, portanto não existe ação observável “esconder pendência” a copiar.

**L65 — Defaults conhecidos e repetição de cadastro.** `herd_add_event_page.dart:70-103`, `herd_screen.dart:1095-1102`, `animal_detail_panel.dart:217-256`. A data atual/tipo da origem/animal do perfil; B preenche contexto conhecido; C data permanece revisável; D abrir/sucesso; E revisão/novo formulário; F salvar e adicionar outro; G comando novo; H não; I animal/fazenda; J erro mantém campos. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: formulários especializados preservam animal/contexto/tipo; cadastro tem “Salvar e adicionar outro” com novo operationId e fatos individuais limpos. Não preservar sexo/mãe indiscriminadamente nem repetir parto/leite automaticamente.

**L66 — Explicação temporal de pendência.** `sanitary_alerts.dart:207-238, 369`, `reproduction_alerts.dart:129-176`. A última aplicação/previsão/hoje; B deriva prazo/dias e razão; C prazo atingido/proximidade; D leitura; E data originária, dias e ação recomendada; F verificar/registrar; G fonte real; H horizonte; I animal; J histórico incompleto explicitado. **IMPLEMENT_NOW** até provar equivalência: pending baseline exibe prazo e alguns últimos fatos, mas intervalo de pesagem é configurável só no servidor e não explicado na UI. Read model deve informar fato, referência e policy aplicada sem hardcode90 no portal; não precisa framework DerivedReason universal.

**L67 — Consequências relacionadas de parto.** `app_state.dart:1119-1258`, `animal_detail_panel.dart:75, 163-201`. A matriz/gestação/cria/eventos; B vínculo materno e encerramento; C mesma operação; D registrar/consultar; E parto, cria e maternidade; F abrir cria/mãe; G fatos relacionados; H remove previsão resolvida; I fazenda; J vínculo ausente explicado. **SUPERSEDED_BY_BETTER_EBOV_MODEL**: jornada de gestação exibe serviço/diagnóstico/parto/cria e CALVED/BORN vinculados; árvore genérica de todos eventos por operationId não era entregue no legado. Não agregar CREATED a operationId inexistente.

## Revalidação das pistas da auditoria antiga

I01–I12 correspondem a L09–L13, L23–L25, L32, L45 e L65. I13–I15/I17 propõem seis contadores de preview de elegibilidade, mas o BovNex ativo não tinha contrato de preview autoritativo: apenas filtrava sexo/status, mostrava total e validava no comando. Não classificar um incremento novo como capability perdida. I16 é L61 (membros iniciais); I 18 é L62; I 19/I20 são L65; I 21–I23 são L13 e sua prévia; I 24/I25 são L21 (múltiplas gerações) e necessidade de evitar N+1; I 26 é L43; I 27 é L63; I 28 é L44/L16; I 29 é L66; I 30 é L67; I 31 é L65. Essa correspondência explicita a revisão e não usa classificação antiga como autoridade.

## Findings para implementação/verificação

1. L17/L20: consumir a regra etária existente no read model operacional e apresentar perfil/lista/Home, sem N+1 nem regra Angular. Rollover DateTime/hora do legado são bugs, não requisitos.
2. L30: preservar dias desde parto real como fato derivado e útil ao planejamento. L31 não autoriza prontidão universal aos45.
3. L54: oferecer impressão/exportação INTERNA que leve contexto de referência/semântica; integração oficial permanece L55.
4. AsOf de leite deve excluir registro posterior à referência; população histórica deve considerar saída/transferência, não status corrente. Relatórios de current-state-aged-at-reference devem nomear explicitamente que não reconstituem população passada.
5. Confirmação reprodutiva não é taxa de prenhez, alerta atrasado não prova parto e ausência de tratamento não prova aplicação.
6. Conferir fatos efetivos sanitários no pending/dashboard/coverage/report; retrações permanecem histórico e não contam estado corrente.

## Limite da prova

Não executados por este inventariador: suíte backend/web, browsers, migrações ou git mutations. Nenhum arquivo em bovnex2/gr-app foi alterado. Não foi emitido veredito COMPLETE. A classificação BEFORE contém IMPLEMENT_NOW e precisa de evidência FINAL por comportamento, não simples existência de classe/endpoint.
