# Implementação e evidências do portal após BEFORE

Data: 02/10/2026. Este relatório complementa `legacy-superset-web-before.md`; o veredito final pertence à revisão/verificação independente. Não foi executado deploy nem alterado bovnex2/gr-app.

| Gap BEFORE | Implementação observável do portal | Evidência |
|---|---|---|
| W01 idade/faixa/transição | Perfil recebe idade derivada no DTO do animal; disclosure explica referência, nascimento e fronteira. Lista mostra idade/faixa sem recalcular calendário. | `gr-web/src/app/features/herd/age-detail.component.ts`, `age-intelligence.models.ts`, `animal-profile-page.component.ts:327`, `animal-list-page.component.ts:352` |
| W02 antecipação em 15 dias | Home mostra preview de 3 com total server-side e ação de exploração; lista tem paginação sem carregar todo rebanho. Loading/empty/error/retry e contexto isolados. | `age-transitions.component.ts`, `gr-web/src/app/features/home/home-page.component.ts:147`, `animal-list-page.component.ts:128` |
| W04 drill-down etário | Contagem da célula abre modal paginado de animais por faixa/sexo/referência, preservando população atual ou histórica. | `gr-web/src/app/features/reports/herd-statements-page.component.ts:158`, `:570`, `gr-web/src/app/features/herd/parity-api.service.ts:131` |
| W05 gradação de parto | Reprodução usa level/guidance recebidos do backend. Perfil também apresenta previsão/dias/fatos e pós-parto de parto registrado, sem declarar aptidão. | `reproductive-intelligence.component.ts`, `reproduction-overview.component.html:31`, `:82` |
| W10 fluxo faixa × sexo | Quadro gerencial completo inclui cadastros, nascimentos, vendas, mortes, transferências, saldos inicial/final e mudança de faixa; inclui total e semântica temporal. | `herd-statements-page.component.ts:202`, `:471` |
| W11 exportação interna | Impressão do quadro integral carregado e opção de salvar PDF pelo navegador, com fazenda e semântica explícitas. Nenhum documento oficial/integração GEDAVE. Não exporta página parcial de relatório paginado. | `herd-statements-page.component.ts:101`, `:602`, `internal-print.ts` |

Adicionais descobertos na varredura completa: disclosure lazy de ascendentes/descendentes em até 5 gerações/100 animais com ampliação explícita até limites backend (20/500), identificação de truncamento por animais ou gerações e restrição à fazenda atual (`animal-lineage.component.ts`); contagem completa e maior nível da agenda por dia no backend, consumida por Saúde/Reprodução/Agenda (`agenda-daily-summary.component.ts`); a contagem sanitária antiga das primeiras 100 agora recebe o agregado completo (`health-command-center.component.html:292`) e itens visíveis são identificados como amostra; prévia de parto consultada por API em formulários individual e lote (`calving-preview.component.ts`), removendo soma de 283 dias no Angular; explicação de pendência de pesagem usa policy/reference/window/explanation reais (`agenda-page.component.ts:280`, `animal-profile-page.component.ts:343`).

## Testes e checks executados

- `npm run check`: exit 0; 72 arquivos, 562 testes aprovados; typecheck app/spec e build aprovados. Log no workspace: `legacy-superset-web-check.log`.
- Após ajustes adicionais (explanation de pesagem, link de gestação, limite de profundidade e ajustes de labels), focused: 6 arquivos / 50 testes aprovados. Log `legacy-superset-web-focused.log`.
- `npm run typecheck:app` e `npm run typecheck:spec` pós-patches: aprovados.
- `git diff --check`: aprovado; somente avisos de normalização LF/CRLF.
- Textos novos revisados em pt-BR; corrigido separador que sofreu conversão no transporte PowerShell.

Suites novas `legacy-intelligence.spec.ts` e `herd-statements-page.component.spec.ts` verificam meses recebidos do backend, contador total independente do preview, loading/empty/error/retry/context switch, orientação factual de pós-parto, lineage lazy/ampliar, preview autoritativo por API, 250 itens no agregado, referência e população preservadas no drill-down, matriz de fluxos e exportação bloqueada em erro.

Warning observado: bundle inicial 678,01 kB vs orçamento de aviso de 650 kB; orçamento não alterado. Classificação como warning preexistente depende da comparação com build baseline registrada pelo coordenador.

## Limites desta evidência

JSDOM e typecheck/build não provam layout/teclado real em 1440/1366/1024/768, comportamento do navegador ao salvar PDF, ou resposta de backend autenticado. Esses itens permanecem no gate de smoke/reviewer/verifier independente. A evidência acima não declara superset completo por si só.
## Revisão de 03/10/2026

Revisão final dos textos novos: pt-BR, acentuação, rótulos curtos e orientação sem afirmações clínicas ou oficiais. A idade no Angular somente formata `completedMonths` recebido; não há cálculo de faixas, transições ou previsão de parto no cliente. A linhagem também mostra a idade recebida do serviço, preservando o contador e a geração.

Findings corrigidos:

- Os testes isolados de revisão em 02/10 expuseram dependência acidental do polyfill `showModal` de outras suítes JSDOM: 3 falhas de teste, sem falha do navegador demonstrada. O polyfill é agora explícito na suíte dos quadros; execução isolada em 03/10 passou com 2 arquivos / 12 testes (`legacy-superset-web-review-tests-2026-10-03.log`).
- Retry da paginação de transições e composição agora preserva a página solicitada antes do erro.
- O link do agregado para Hoje mantém `includeOverdue`, preservando as pendências anteriores que integram a contagem.
- Botões do drill-down têm rótulo ARIA contextual da faixa, sexo e contador.
- Animais históricos fora da custódia atual exibem snapshot/fallback “Animal do histórico” e nenhuma ação de abrir perfil fora do escopo (`availableInCurrentFarm`).
- Pós-parto mostra contagem regressiva até a revisão ou referência alcançada/prevista para hoje, a partir do DTO recebido. Não declara aptidão reprodutiva.
- Linhagem trata separadamente limite de animais e de gerações, oferecendo ampliar a consulta quando possível.

`typecheck:spec` e `git diff --check` da revisão de 03/10 aprovados. A suíte completa final e a comparação visual continuam sob execução do coordenador; esta revisão não executou novamente `npm run check` completo. Os números 72/562 e 6/50 acima identificam execuções de 02/10, antes dos últimos findings, e não são apresentados como gate final da missão.
Atualização da revisão: o DTO etário agora inclui `completedDays`; para animais com menos de um mês completo, o portal formata 0/1/N dias recebidos do backend. A linhagem mostra idade por nó e referência global do serviço. Não existe diferença de datas calculada no navegador para a idade. Typecheck app/spec após esses ajustes aprovados.
