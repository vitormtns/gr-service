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
- decisões arquiteturais documentadas;
- Spring Security Resource Server com validação JWT em modos explícitos JWKS e HMAC;
- identidade imutável da requisição e endpoint protegido `GET /api/v1/me`;
- erros JSON para 401/403 e user ID seguro no contexto de logs.

## Próxima etapa

Adicionar conexão JDBC runtime e sincronizar idempotentemente o claim `sub` com o usuário interno. Depois, resolver e validar organização e fazenda ativas, criar o contexto multi-tenant, propagá-lo transacionalmente ao PostgreSQL e implementar repositories tenant-aware.

Antes de codificar, detalhe os limites entre identidade, organização e fazenda e defina como permissões e seleção de fazenda serão validadas. Não implemente rebanho, reprodução, sanidade ou assinaturas junto dessa etapa.

## Etapas posteriores, condicionadas a casos reais

Outbox transacional, idempotência persistida, jobs, auditoria, sincronização offline, réplicas, cache e Event Sourcing seletivo. Cada capacidade deve entrar com consumidor, teste e critério operacional claros.
