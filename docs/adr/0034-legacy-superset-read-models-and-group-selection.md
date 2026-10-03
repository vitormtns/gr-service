# ADR 0034 — Inteligência derivada e seleção operacional do rebanho

## Estado

Aceita em 02/10/2026.

## Contexto

A comparação comportamental com o BovNex revelou regras de idade sem superfície operacional, linhagem limitada a relações diretas, ausência de composição dos fluxos por faixa e sexo e repetição da seleção entre módulos. Preservar nomes de funcionalidades não comprova equivalência desses comportamentos.

## Decisão

O monólito mantém regras determinísticas no domínio e read models explícitos no módulo `herd`. Não há scheduler etário, faixa ou idade persistida, microserviço, cache funcional ou Event Sourcing novo.

`AgeIntelligence` deriva nascimento, referência, meses completos, faixa atual, próxima faixa, fronteira e data da transição exclusivamente de `AgePolicy`. A lista e o perfil usam o Clock injetado uma vez por consulta. Nascimento desconhecido ou posterior à referência produz ausência de inteligência. `/age-intelligence/transitions` consulta somente ativos da fazenda atual; horizonte padrão contratado de 15 dias, inclusivo e limitado a 366 dias. Não representa posição histórica. Seleção e contagem são set-based no servidor, com fronteiras da policy e ajuste de fim de mês verificado em PostgreSQL.

Posição atual classificada em uma referência e posição histórica são contratos distintos. A segunda reconstrói entradas e saídas até `asOf`. Faixa e sexo dos fluxos usam a data de cada fato. Nascimento e criação não são duas entradas da mesma cria. Mudança de faixa tem saldo assinado, sem clamp. Datas de período são inclusivas; abertura é o dia anterior ao início. Não se introduz bitemporalidade: correções cadastrais podem corrigir retrospectivamente a classificação. Referências individuais históricas preservam a sanitização do ADR 0031.

A linhagem usa SQL recursivo, interrompe caminhos fora da fazenda autorizada e evita ciclos. Profundidade e quantidade são limitadas; a resposta explicita esses limites. Parentesco não concede autorização transitiva. Crias diretas usam JOIN paginado, eliminando N+1.

A reprodução deriva gradação temporal da previsão e referência. Previsão não comprova parto. Pós-parto usa somente evento `CALVED` real e marco configurável de avaliação; nunca declara aptidão automática para cobertura. A prévia usa a policy do servidor, sem duplicar 283 dias no Angular.

A agenda agrega contagem e maior nível diário no servidor. Próxima aplicação em `nextDueOn` aparece antes de vencer como planejamento de manejo, sem transformar periodicidade antiga em obrigação legal. Tratamentos retraídos permanecem históricos e não influenciam estado operacional.

Seleções de lista e grupo são explícitas, limitadas a 100 animais por comando. O portal revisa a população inteira dentro desse limite, reconsulta estado e versões e apresenta exclusões; não amplia uma página silenciosamente. `/groups/with-animals` cria grupo e membros atomicamente. `/groups/{id}/animals/batch` valida versão e associa integralmente, com SQL set-based e as mesmas permissões de gerenciamento de grupos.

`app.herd_group_operations` guarda recibo append-only por tenant/fazenda/operationId. Payload canônico ordena seleção; replay devolve resultado original. Payload diferente produz conflito. A tabela tem FK composta, RLS/FORCE RLS e SELECT/INSERT para `app_api`. Nenhuma migration remota integra esta decisão.

## Apresentação e relatórios internos

O portal apresenta pt-BR, disclosure no perfil, resumo e exploração na Home, paginação e cancelamento na troca de contexto. Impressão de quadro integral identifica fazenda e semântica; navegador pode salvar em PDF. Não se imprime primeira página como relatório completo. Documento interno não comprova aceitação, envio, conformidade ou integração oficial com GEDAVE.

## Validação

Domínio testa calendário e fronteiras; integração usa migrations reais, role runtime, RLS, fazenda, atomicidade, replay e história. ArchUnit verifica fronteiras de módulo. O fechamento exige testes da UI e comparação independente de comportamento observável.
