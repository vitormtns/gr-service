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
18. Crie tabelas de negócio somente no schema privado `app`; não exponha esse schema diretamente a clientes.
19. Foreign keys entre tabelas tenant-scoped devem incluir `tenant_id` quando necessário para impedir relações cruzadas.
20. Índices de consultas operacionais devem começar por `tenant_id` quando o acesso for delimitado pelo tenant.
21. UUIDs de agregados devem aceitar valores gerados antecipadamente pela aplicação ou por clientes offline autorizados.
22. RLS é defesa complementar e não substitui filtros explícitos por tenant em repositories.
23. Testes devem executar as migrations reais de `supabase/migrations`; não duplique SQL em recursos de teste.
24. Nenhuma entidade ou migration de negócio deve referenciar diretamente `auth.users`.
25. Clientes web e mobile acessam dados de negócio pela API Spring, nunca diretamente pelas tabelas do Supabase.
26. Nunca interprete JWT manualmente em controller; a validação e conversão pertencem à infraestrutura do Spring Security.
27. Use o UUID validado de `sub` como identidade canônica. E-mail é atributo opcional, nunca identidade primária.
28. Roles do token Supabase são apenas técnicas e nunca viram papéis organizacionais, membership ou permissão de negócio.
29. Nunca registre bearer token, assinatura, segredo HMAC, refresh token, headers de autorização ou claims completos.
30. Nunca aceite tenant ou fazenda apenas por claim ou header não validado contra dados da aplicação.
31. Segredo HMAC pertence somente ao backend. Prefira JWKS quando o emissor usar assinatura assimétrica e nunca faça fallback silencioso entre modos.
32. Testes de autenticação devem usar material criptográfico efêmero ou exclusivo de teste e não depender do Supabase remoto.
33. Repositories JDBC devem usar SQL qualificado, parâmetros preparados e transações Spring; não introduza ORM ou geração automática de schema.
34. A conexão runtime usa login dedicado `NOINHERIT` e assume `app_api` com `SET LOCAL ROLE` na transação; nunca use superusuário em runtime.
35. A sincronização de identidade usa `sub` como PK, preserva valores ausentes e não reativa usuários suspensos ou desativados.
42. IDs enviados pelo cliente nunca comprovam acesso; `TenantContext` é imutável e existe somente como atributo da requisição.
43. Contexto de tenant não pode usar `ThreadLocal`; apenas endpoints opt-in podem resolvê-lo e role/escopo vêm do banco.
44. Esta fase não configura `app.current_tenant_id`.

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

## Bootstrap de organizações

1. Nunca liste organizações antes de validar e sincronizar o usuário autenticado.
2. Nunca receba `userId` do cliente para bootstrap de organizações ou tenancy.
3. O contexto de usuário para bootstrap deve ser transacional e configurado com `set_config(..., true)`.
4. Funções `SECURITY DEFINER` devem usar `search_path` fixo e seguro, sem SQL dinâmico.
5. Revogue a execução de funções de bootstrap de `PUBLIC`; conceda-a somente à role de aplicação necessária.
6. Papéis organizacionais são lidos de `organization_memberships.role_key`, nunca de claims do JWT.
7. `organizationId` informado pelo cliente nunca prova acesso e `userId` nunca é recebido para listar fazendas.
8. `ALL_FARMS` nunca ignora os estados da fazenda; `SELECTED_FARMS` exige escopo do mesmo membership e tenant.
9. O bootstrap de fazendas não cria `TenantContext` nem configura `app.current_tenant_id`.
