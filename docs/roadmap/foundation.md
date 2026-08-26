# Roadmap da fundação

## Concluído nesta etapa

- Spring Boot com Java 21, Maven Wrapper, Actuator e Validation;
- estrutura de monólito modular e testes ArchUnit;
- contratos mínimos de command/query, evento de domínio e tenancy;
- request ID, correlation ID e convenção de erro de validação;
- perfis local e teste, CI e scripts de verificação;
- configuração versionada do Supabase local, sem migrations ornamentais;
- decisões arquiteturais documentadas.

## Próxima etapa

Criar a fundação de identidade, organizações, fazendas e isolamento multi-tenant. Essa etapa deve incluir autenticação por JWT do Supabase, modelo mínimo de vínculo de usuário, resolução segura do `TenantContext`, primeiras migrations e testes de integração com PostgreSQL/Testcontainers.

Antes de codificar, detalhe os limites entre identidade, organização e fazenda e defina como permissões e seleção de fazenda serão validadas. Não implemente rebanho, reprodução, sanidade ou assinaturas junto dessa etapa.

## Etapas posteriores, condicionadas a casos reais

Outbox transacional, idempotência persistida, jobs, auditoria, sincronização offline, réplicas, cache e Event Sourcing seletivo. Cada capacidade deve entrar com consumidor, teste e critério operacional claros.
