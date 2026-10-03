# Encerramento funcional do BovNex — eBov

Auditoria iniciada em 02/10/2026 e encerrada após validação local em 03/10/2026. Este relatório e a matriz AFTER delimitam o legado como fonte de requisitos, com equivalência funcional, substituições fundamentadas e exceções explícitas.

### Congelamento do legado

Com a integração desta missão em `main`, `bovnex2` passa a ser exclusivamente histórico e somente leitura. Não é fonte de verdade do domínio, requisito normativo ou baseline arquitetural, e não deve ser consultado por padrão ao criar novas funcionalidades. O eBov atual é a referência funcional. A matriz final preserva as exceções de GEDAVE oficial (`REGULATORY_BLOCKED`) e offline/sincronização do futuro cliente (`CLIENT_ONLY_FUTURE_APP`). Os estados de branches e working trees descritos abaixo registram o encerramento da auditoria antes do merge final.

### Baseline discovered

Backend: `main`, `a7490c7cfd83198f685416b2380910814904150f`; portal: `main`, `f08e5bc862d39fc9a5016f064a566e2bd7fd24fb`; BovNex: `main`, `717a84e30e079a8c5a8b48dcc819b677c2981269`. Fetch confirmou `main...origin/main = 0/0` nos dois repositórios alterados. Branches de trabalho: `feature/bovnex-superset-closure`, sem merge ou push. Backend baseline: 616 testes, incluindo oito de arquitetura, e verify aprovados. Web baseline: 543 testes em 70 arquivos, typechecks e build aprovados. `.maestri/` e `brag-plan.md`, não versionados, eram preexistentes e foram preservados.

### Legacy repository map

O [inventário integral](legacy-superset-legacy-inventory.md) registra rotas, estado, modelos, serviços, persistência/sincronização, componentes e relatórios. As 67 entradas incluem as descobertas adicionais, referências primárias e a distinção entre código consumido, páginas sem rota e promessas sem ação.

### Behavioral parity methodology

Comparação A–J: fatos, regra, fronteiras, gatilho temporal, informação, ação, referência histórica, antecipação, escopo e vazio/erro. Classe ou endpoint isolado não prova equivalência. Cada linha especifica comportamento, domínio, consumo web e diferenças. Testes relacionados de uma família não são apresentados como prova exclusiva de cada entrada.

### BEFORE matrix

Documentos produzidos antes dos patches: [legado e A–J](legacy-superset-legacy-inventory.md), [backend](legacy-superset-backend-before.md) e [web](legacy-superset-web-before.md). Preservam o estado descoberto e as lacunas originais.

### Valid gaps discovered

Fechados: idade neonatal e próxima faixa; linhagem multigeração; crias sem N+1; seleção explícita para manejo/grupos; criação e associação atômica de grupos; prévia reprodutiva centralizada; gradação de atraso e acompanhamento após parto real; explicação de pesagem; agregado diário integral; vencimentos sanitários futuros na agenda; detalhes de composição; conciliação de fluxos por faixa/sexo; impressão integral interna.

### Obsolete/unsafe legacy behavior

Não reproduzidos: apagamento destrutivo de fatos, nascimento inferido por previsão, aptidão clínica universal após 45 dias, obrigação anual de aftosa, ações fictícias e alternativas sem rota. As justificativas individuais estão na matriz. As categorias acessíveis de exploração foram reconhecidas como comportamento real e substituídas por filtros, regras de grupo e composição, preservando a intenção sem inferir aptidão.

### Regulatory blocked behavior

L55: declaração/exportação oficial GEDAVE permanece `REGULATORY_BLOCKED`, por falta de contrato oficial e requisitos verificáveis. A leitura e impressão gerencial interna permanecem disponíveis, sem assumir valor de declaração oficial.

### Client-only behavior

L56: offline local, outbox, contador e sincronização manual permanecem `CLIENT_ONLY_FUTURE_APP`. A missão exige preservar `gr-app`; a exceção fica registrada para o futuro cliente móvel.

### Backend changes

Read models de idade, transições e linhagem usam referência do servidor e escopo autorizado. Seleção manual usa transação, locks, versão e receipt persistente por operação; replay devolve o resultado original, payload divergente conflita. A migração `20261002190709_herd_group_batch_operations.sql` cria tabela privada com FORCE RLS e privilégios mínimos, aplicada exclusivamente ao Supabase local. Agenda e relatórios agregam no PostgreSQL com os filtros dos detalhes. [Contratos](legacy-superset-backend-capabilities.md) e [ADR 0034](../adr/0034-legacy-superset-read-models-and-group-selection.md).

### Web changes

Home/lista/perfil consomem idade e transições. Perfil oferece detalhes progressivos de linhagem e reprodução. Lista/grupos oferecem seleção explícita, reconsulta e revisão antes de confirmar manejo ou associação. Agenda/saúde/reprodução usam contagens integrais. Quadros têm detalhes paginados e impressão interna. [Evidência web](legacy-superset-web-after.md).

### Age intelligence

`COMPLETED_CALENDAR_MONTHS_V1`: meses completos, dias completos para neonatos e fronteiras 3/9/13/25/37. Nascimento desconhecido ou futuro não inventa idade. Transições respeitam fim de mês, horizonte inclusivo e animais ativos da fazenda autorizada. Não há contador persistido ou fórmula concorrente no Angular.

### Reproductive intelligence

Prévia usa a política do servidor, atualmente 283 dias. Faixas de atraso 0, 1–6, 7–13 e 14+ orientam acompanhamento sem presumir parto/diagnóstico. Pós-parto deriva exclusivamente de `CALVED` efetivo; prazo configurável de revisão não declara aptidão. Referências e fontes permanecem explícitas.

### Sanitary intelligence

Fatos sanitários efetivos e retrações preservam a política existente de brucelose. Agenda inclui `nextDueOn` futuro no período; pendências continuam restritas ao devido. Pesagem explica último fato, referência, corte e janela configurada. Aftosa é registro histórico, sem obrigação automática restaurada.

### Historical/as-of semantics

Posição histórica reconstitui participação por eventos até `asOf`, usando sexo/nascimento atualmente corrigidos. Não representa o conhecimento disponível naquela data. Identidade de animal que saiu vem do snapshot histórico autorizado ou fica indisponível; não usa nome atual de outra fazenda. Perfil é desabilitado quando o animal não está disponível no escopo atual.

### Reporting improvements

Fluxos conciliam posição no dia anterior ao início, entradas/saídas nas datas dos eventos e saldo na data final. Nascimento não duplica cadastro; mudança de faixa concilia envelhecimento entre posições. Datas desconhecidas têm célula própria. Detalhes são paginados/contextuais. Impressão usa o quadro integral, com fazenda, referência e semântica, em documento isolado. HTML/PDF real de fluxos verificado com 16 linhas, incluindo faixas, desconhecidos e total.

### Regulatory-safe reporting

Quadros/impressão exibem apoio gerencial, sem declaração ou envio oficial GEDAVE. Registro de vacinação não comprova imunidade/conformidade; ausência de registro não prova ausência de vacinação.

### Explainability

Idade mostra nascimento, referência, política e fronteira. Reprodução distingue previsão e fato. Pesagem mostra origem/janela. Linhagem informa limites e vínculos adicionais. Relatórios distinguem atual/histórico/eventos e população desconhecida.

### Test evidence

Backend: `mvnw.cmd verify` aprovado em 03/10/2026 às 10:07:59, com 641 testes, zero falhas/erros/skips e empacotamento do JAR concluído. `ArchitectureTest`: oito testes aprovados. Execução focada final: 33 testes aprovados, incluindo domínio etário, contrato HTTP, grupo/linhagem, agenda, relatórios PostgreSQL e arquitetura (`superset-final-backend-focused.log`).

Web: `npm run check` aprovado com 569 testes em 72 arquivos, typecheck de app/spec e build. Testes focados finais: 23 aprovados em três arquivos (inteligência, seleção e relatórios). `git diff --check` aprovado nos dois repositórios, inclusive na revisão do conteúdo preparado para commit.

Logs ficam no diretório pai: `superset-final-backend-verify.log`, `superset-final-web-check.log` e `superset-evidence/`. Smoke: Playwright local autorizado, Edge headless, login real, fixtures existentes, sem mocks de API ou gravação de negócio. Sete rotas em 1440/1366/1024/768 px, perfil/linhagem, revisão de grupo, detalhes por teclado e impressão. Dimensões de contêineres também verificam conteúdo cortado por overflow oculto.

Falhas intermediárias corrigidas e gates repetidos: descoberta Docker; mapper de datas do teste; duas listas de limpeza com nova FK; inventários schema/RLS; contagem antiga dos campos HTTP. Uma execução passou os 641 testes, mas falhou no repackage porque a JVM do smoke mantinha o JAR aberto no Windows; o processo foi encerrado e o verify integral repetido com sucesso. Esse log foi preservado em `superset-backend-tests-before-package-unlock.log`. Nenhum teste foi desativado para ocultar falha. Warnings preexistentes: Mockito/Byte Buddy sobre agente dinâmico; bundle web 678,01 kB, excedendo budget 650 kB em 28,01 kB. Budget preservado. Logs negativos HTTP são cenários esperados.

### Reviewer findings

[Backend independente](legacy-superset-backend-review.md) e [cobertura](legacy-superset-review.md). Corrigidos: grupo consultado em chamada única até 100; idade neonatal/nós de linhagem; dias até revisão pós-parto; identidade histórica após transferência. Corrigidas também classificações de categorias acessíveis e referências de testes do domínio incorreto.

### Verifier findings

[Verificador independente](legacy-superset-verifier.md). Corrigido corte do quadro em 768 px por largura mínima dos itens de grid; smoke passou a exigir bounds dos filtros/contêineres. Detalhe por teclado tornou-se obrigatório, eliminando skip silencioso do seletor. Singular da revisão de grupo corrigido. O parecer final considera os gates concluídos e a rastreabilidade da matriz; não há finding de código aberto.

### AFTER matrix

[67 capacidades](legacy-superset-after-matrix.md), com legado, evidência, comportamento atual, backend/web, classificação, razão, testes e commits. `CLIENT_ONLY_FUTURE_APP`: 1; `FULL_PARITY`: 10; `OBSOLETE_OR_UNSAFE`: 12; `REGULATORY_BLOCKED`: 1; `SUPERSEDED_BY_BETTER_EBOV_MODEL`: 43. Total: 67.

### Remaining exceptions

Apenas L55, regulatória, e L56, exclusiva do futuro cliente. Exclusões inseguras têm motivos individuais. Não resta `IMPLEMENT_NOW`.

### Final parity verdict

`EBOV_LEGACY_SUPERSET_COMPLETE_WITH_CLIENT_AND_REGULATORY_EXCEPTIONS`

### Commits

gr-service:

- `31f9c61` — feat(herd): centralize age intelligence and transition queries

- `696886a` — feat(herd): add bounded maternal lineage and set-based calf reads

- `b525cc8` — feat(herd): create and populate manual groups atomically

- `92e2bc9` — feat(herd): explain reproductive windows and aggregate daily agenda

- `6153544` — feat(reports): reconcile age-sex flows and scope historical details

gr-web:

- `ead3b71` — feat(herd): display age transitions lineage and reproductive guidance

- `773d5c4` — feat(herd): review selected animals for treatment breeding and groups

- `a806db9` — feat(herd): consume complete daily summaries in agenda and health

- `4c22b5a` — feat(reports): drill into composition and print complete internal statements

O commit documental final contém esta matriz, auditorias, ADR e parecer do verificador; seu hash é consultável com `git log -1` na branch do backend.

### Working trees

Na branch `feature/bovnex-superset-closure`, código backend e web integralmente commitados. Após o commit documental, backend conserva somente `.maestri/` não versionado e web somente `brag-plan.md` não versionado, ambos preexistentes. BovNex continua limpo em `717a84e`. `gr-app` não é um repositório Git independente neste workspace e não recebeu alterações. Servidores Angular/Java iniciados para o smoke foram encerrados; o Supabase local preexistente foi mantido. Nenhum deploy ou push.

### Explicitly untouched files

`bovnex2`, `gr-app`, `.maestri/`, `brag-plan.md`, budgets, branches `main`, migrations remotas, deploy e dados de negócio preservados. Nenhum push, merge em main ou envio regulatório. Migração nova aplicada somente localmente; testes de comandos usam PostgreSQL efêmero via Testcontainers.
