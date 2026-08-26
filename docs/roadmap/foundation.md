# Roadmap da fundação

## Concluído nesta etapa

- Spring Boot com Java 21, Maven Wrapper, Actuator e Validation;
- estrutura de monólito modular e testes ArchUnit;
- contratos mínimos de command/query, evento de domínio e tenancy;
- request ID, correlation ID e convenção de erro de validação;
- perfis local e teste, CI e scripts de verificação;
- configuração versionada do Supabase local, sem migrations ornamentais;
- schema privado `app` com usuários, organizações, fazendas, memberships e escopos por fazenda;
- constraints compostas multi-tenant, role `app_api`, contexto transacional e RLS;
- migrations reais validadas em PostgreSQL 15 com Testcontainers;
- decisões arquiteturais documentadas.

## Próxima etapa

Implementar Spring Security Resource Server, validar JWTs do Supabase e sincronizar o claim `sub` com o usuário interno. Depois, resolver com segurança o `TenantContext` e a fazenda ativa, criar repositories tenant-aware e expor os primeiros endpoints de identidade, organização e fazenda.

Antes de codificar, detalhe os limites entre identidade, organização e fazenda e defina como permissões e seleção de fazenda serão validadas. Não implemente rebanho, reprodução, sanidade ou assinaturas junto dessa etapa.

## Etapas posteriores, condicionadas a casos reais

Outbox transacional, idempotência persistida, jobs, auditoria, sincronização offline, réplicas, cache e Event Sourcing seletivo. Cada capacidade deve entrar com consumidor, teste e critério operacional claros.
