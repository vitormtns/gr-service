# Verificador final de paridade BovNex → eBov

Data: 03/10/2026. Verificação de evidências, contratos e comportamento observável do estado atual. A releitura cruza o inventário primário de 67 microcapabilities, a matriz AFTER, os relatórios de revisão independente, testes executados e o smoke autenticado local. A revisão independente das famílias implementadas pelo agente web consta de `legacy-superset-review.md`; a revisão de idade/linhagem/grupos/seleção consta de `legacy-superset-backend-review.md`. Este documento consolida as evidências e não apresenta a autoavaliação de uma implementação como única revisão independente.

## Cobertura e classificação

A matriz AFTER contém exatamente 67 IDs únicos L01–L67 e classificação única por entrada. Contagem conferida por leitura estrutural:

| Classificação | Entradas |
|---|---:|
| FULL_PARITY | 10 |
| SUPERSEDED_BY_BETTER_EBOV_MODEL | 43 |
| IMPLEMENT_NOW | 0 |
| OBSOLETE_OR_UNSAFE | 12 |
| REGULATORY_BLOCKED | 1 |
| CLIENT_ONLY_FUTURE_APP | 1 |

As 53 capacidades válidas atuais possuem equivalente ou sucessor com intenção de negócio explícita. A conclusão não se baseia apenas na presença de classes: os documentos descrevem fatos, limites, gatilhos, informação, ação, referência histórica, antecipação, escopo e vazio/erro (A–J), e apontam a superfície consumidora.

Exceções explícitas: L55 exclui somente declaração/exportação/envio oficial GEDAVE, preservando composição, posição histórica, reconciliação e impressão gerencial interna; L56 mantém offline/outbox/sincronização como backlog do cliente futuro, sem editar gr-app. As 12 exclusões fundamentadas abrangem eliminação destrutiva de fatos, inferência de parto/aptidão exclusivamente pelo calendário, obrigação atual de aftosa anual, denominadores/fórmulas artificiais e promessas/código sem fluxo entregue. Não foram convertidas genericamente em SUPERSEDED.

## Rechecagem dos pontos sensíveis

- Idade e transições: nascimento + referência determinística alimentam `AgePolicy` e `AgeIntelligence`; o portal apenas formata completedMonths/completedDays, faixas/data/fronteira recebidas. Home/Animais/Perfil consomem a informação. Não há persistência de idade/faixa nem job diário. O recém-nascido observado no smoke tem 1 dia e a linhagem apresenta idade por parente.
- Histórico: posição e composição usam referência explícita; fluxo agrupa idade na ocorrência e reconcilia envelhecimento. Drill-down histórico usa snapshot de identificação/nome da fazenda e não oferece perfil fora da custódia atual (`availableInCurrentFarm`). Não foi alegada bitemporalidade.
- Reprodução: possível/confirmada/previsão/parto/encerramento têm estados e fatos reais. A gradação temporal e orientação vêm do serviço; pós-parto depende de CALVED registrado e mostra dias decorridos e restantes para revisão, sem declarar aptidão. Preview de parto é consultado pela API; soma de 283 dias foi retirada do Angular.
- Sanidade: política estruturada de brucelose e effective facts preservados; retração não apaga trilha. Aftosa aparece como registro histórico; vermifugação/nextDueOn são manejo. Agenda inclui próximos fatos previstos e contagem integral por dia/maior nível, sem inferir obrigação anual ou confundir tarefa concluída com cuidado realizado.
- Seleção: conjunto explícito limitado a 100, consulta única para snapshot de grupo e revalidação de perfis autorizados antes da revisão. Replay/versionamento/isolamento são cobertos por testes de integração; o smoke abre revisão sem confirmar comandos.
- Relatório interno: quadro de fluxo mostra todas as células e total, referência/período/fazenda e semântica inclusiva; impressão usa o quadro integral, sem sugerir arquivo aceito ou declaração oficial GEDAVE.

## Gates observados

| Gate | Evidência executada | Resultado observado |
|---|---|---|
| Portal typecheck app/spec | `superset-final-web-check.log` | Aprovados |
| Portal suíte completa | Mesmo log | 72 arquivos, 569 testes aprovados |
| Portal build | Mesmo log | Concluído; bundle 678,01 kB, warning preexistente frente a 650 kB; budget não alterado |
| Backend suíte completa PostgreSQL/Testcontainers | `superset-final-backend-verify.log` | 641 testes; 0 falhas, 0 erros, 0 ignorados |
| ArchitectureTest | Mesmo log | 8 testes aprovados |
| Backend verify/repackage | Mesmo log, encerrado em 03/10/2026 às 10:07:59 −03:00 | BUILD SUCCESS; JAR construído e repackage concluído após liberar o processo local do smoke |
| Whitespace | `git diff --check` em ambos repositórios, leitura do verificador | Sem erros; avisos LF/CRLF não são falhas |
| Smoke autenticado local | `superset-evidence/visual-smoke.json` e `smoke.mjs` | 28 páginas em 1440/1366/1024/768, mais perfil/disclosures, revisão de grupo, composição, fluxo e impressão; errors=[] e responses=[] |

O smoke navega Home, Animais, Grupos, Saúde, Reprodução, Agenda e Quadros em quatro larguras. Perfil foi aberto pela rota real do animal, com disclosures acionados por teclado Enter. A composição foi aberta por botão acessível com Enter e fechada com Escape; a revisão de grupo também foi aberta/fechada sem submit. O modal observado contém 8 animais da célula consultada. JSON final tem 36 registros de evidência, incluindo métricas de fluxo com `clipped=[]` nas quatro larguras.

O verificador inspecionou as imagens do perfil e dos fluxos em 768. O primeiro screenshot revelou expansão intrínseca do wrapper e corte de filtros/textos, apesar de document.scrollWidth coincidir com a viewport. Finding encaminhado e corrigido pelo principal com min-width/max-width nos itens de grid. A nova imagem mostra navegação quebrando linha, filtros/botão acessíveis, texto integral e scroll da tabela confinado ao quadro; o smoke passou a conferir boundingRects para evitar falsa aprovação por overflow oculto.

Exportação interna observada: HTML gerado pelo fluxo de impressão tem 16 linhas de tabela (cabeçalho, 14 células e total), contexto da fazenda e exclusão de declaração/envio oficial. O mesmo HTML gerou `quadro-fluxos-interno.pdf`. Isso comprova geração do documento interno; não representa aceitação regulatória nem execução do diálogo nativo “Salvar como PDF” de todos os navegadores.

## Findings finais e situação

| Finding | Situação |
|---|---|
| Locator de smoke poderia pular composição por diferença de caixa no rótulo | Corrigido; script passou a exigir célula real e modal com 8 animais, sem skip silencioso |
| Overflow oculto mascarava quadro/filtros em 768 | Corrigido; screenshot e boundingRects novos aprovados |
| Mensagem “os 1 animais” no modal de seleção | Código corrigido para singular/plural natural; não altera regra de negócio |
| Falhas de contrato de animal, 7 campos esperados e 8 atuais | Expectativas antigas corrigidas; última suíte integral passou 641/641 |
| Teste de modal dependia de polyfill de outra suíte | Polyfill explícito local; testes isolados aprovados |
| JAR mantido aberto pelo smoke impede repackage | Resolvido após encerrar o processo local; nova execução integral de verify terminou em BUILD SUCCESS. A tentativa anterior permanece em `superset-backend-tests-before-package-unlock.log` |

Não foi encontrado IMPLEMENT_NOW válido remanescente na rechecagem das 67 entradas. A matriz final apresenta os hashes dos commits por capacidade, inclusive referências de baseline nas exclusões e capacidades preservadas. Os gates finais foram concluídos e conferidos nos logs, incluindo o empacotamento do backend; o warning de bundle permanece explícito e os budgets não foram alterados.

Parecer final: **EBOV_LEGACY_SUPERSET_COMPLETE_WITH_CLIENT_AND_REGULATORY_EXCEPTIONS**. O encerramento se limita às capacidades auditadas do serviço e portal, com as exceções L55/L56 e exclusões fundamentadas descritas acima. Nenhum gate pendente foi aprovado por inferência.

## Limites da evidência

O smoke utiliza registros locais já existentes e usuário autorizado da fazenda de teste. Não criou, alterou ou confirmou dados pecuários/financeiros; login estabelece a sessão de teste e os formulários param na revisão. Não substitui testes de escrita, rollback, replay ou isolamento: esses estão na suíte PostgreSQL. As telas vazias encontradas são estados reais da população de teste; cenários não presentes nela, como transição próxima e gradações reprodutivas, são comprovados por contratos/testes determinísticos e revisão do consumo web, sem inserir animais fake no smoke.

Não houve deploy, migration remota, envio GEDAVE, alteração do BovNex ou gr-app. Fotos/backup prometidos sem consumidor não são funcionalidades entregues; offline permanece backlog explícito. Nenhuma afirmação de compliance oficial ou de operação em produção decorre deste documento.
