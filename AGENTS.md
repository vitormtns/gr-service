# Orientações para agentes de código

Estas regras valem para todo o repositório. Antes de uma decisão arquitetural, leia `docs/architecture/` e os ADRs relacionados.

## Regras obrigatórias

1. Organize código por módulo de negócio e respeite os limites descritos em `module-conventions.md`.
2. Um módulo não pode acessar diretamente tabelas, repositórios ou detalhes internos de outro módulo.
3. Controllers traduzem HTTP e delegam casos de uso; não contêm regras de negócio.
4. Flutter e Angular não acessam nem alteram diretamente tabelas de negócio. Toda regra pertence à API.
5. Não altere o banco manualmente: toda mudança estrutural exige migration versionada no Supabase CLI.
6. Toda tabela de negócio deve possuir isolamento explícito por tenant, salvo exceção documentada em ADR.
7. Toda consulta operacional deve filtrar o tenant resolvido pelo backend, nunca um `tenantId` aceito livremente do cliente.
8. Toda operação crítica deve avaliar idempotência, repetição de mensagens e retries.
9. Eventos devem ter tipo e versão explícitos; mudanças incompatíveis exigem nova versão.
10. Não adote Event Sourcing automaticamente. Exija benefício concreto e um ADR específico para o agregado.
11. Não adicione Kafka, RabbitMQ, Redis, cache distribuído ou outra infraestrutura sem requisito comprovado.
12. Não versione segredos, arquivos `.env`, tokens, chaves administrativas ou dados pessoais reais.
13. Não faça commit, push, pull request ou mudança remota sem solicitação explícita.
14. Execute `./mvnw verify` ou `.\mvnw.cmd verify` antes de declarar uma alteração concluída.
15. Atualize documentação e ADRs quando uma decisão arquitetural mudar.
16. Preserve a aplicação stateless: nada de estado funcional em singleton mutável, sessão HTTP ou arquivo local.
17. Mantenha textos visíveis e documentação em português do Brasil correto e natural. Nomes técnicos no código podem permanecer em inglês.

## Convenções de implementação

- O domínio não depende de Spring, API ou infraestrutura.
- A aplicação coordena casos de uso por command/query e depende de portas, não de adaptadores concretos.
- A API valida entrada, resolve identidade e tenant e converte resultados para HTTP.
- A infraestrutura implementa persistência e integrações.
- Prefira chamadas diretas a handlers. Não crie buses, registries, reflexão ou frameworks internos sem necessidade real.
- Dados sensíveis, JWTs, credenciais e conteúdo pessoal não devem aparecer em logs.

## Relatório final esperado

Toda tarefa deve informar de forma objetiva:

1. diagnóstico inicial, branch e alterações locais preexistentes;
2. arquivos e comportamentos alterados;
3. comandos de validação executados e seus resultados;
4. quantidade de testes e situação das regras ArchUnit;
5. limitações, decisões adiadas e desvios justificados;
6. `git status` final;
7. próximo passo recomendado, sem implementá-lo sem autorização.
