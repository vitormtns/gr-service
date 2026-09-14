# ADR 0033 — Backend MVP readiness e freeze funcional

- Status: Aceito
- Data: 14/09/2026

## Contexto

As Phases 05 a 10 concluíram os domínios funcionais do Backend MVP. Continuar antecipando funcionalidades aumentaria a superfície sem feedback dos clientes. Antes do Portal Angular e do aplicativo Flutter, a API precisava de contrato consumível, probes operacionais, correlação, configuração segura, auditoria de banco e uma prova curta entre módulos.

## Decisão

Adotamos a seguinte fronteira de productization:

1. Springdoc deriva OpenAPI do código, agrupado por domínio e com bearer JWT, headers de contexto, respostas de erro e exemplos úteis.
2. OpenAPI/Swagger ficam habilitados no perfil local e desabilitados por padrão e no perfil de produção.
3. Toda requisição recebe request/correlation ID validado ou gerado. MDC carrega IDs seguros de request, correlação, usuário, tenant e fazenda e é limpo ao final.
4. Actuator expõe somente health. Liveness não depende do banco; readiness inclui o PostgreSQL.
5. O erro transversal mantém envelope previsível e mensagens sanitizadas. Stack trace, SQL, constraints, tokens e payloads não são registrados no fluxo operacional.
6. CORS usa propriedades tipadas e uma allowlist explícita. Configuração inválida falha no startup.
7. A segurança do banco passa a ter teste de inventário: RLS forçada nas tabelas tenant-aware, `SECURITY DEFINER` com `search_path` fixo e sem execução pública, role runtime com least privilege, índice por tenant e ausência de índices idênticos.
8. Migrations históricas não são editadas; correções são aditivas. O índice financeiro redundante foi removido por nova migration.
9. A suíte smoke usa aplicação e PostgreSQL reais para atravessar autenticação/contexto, rebanho, planner, inventário, finanças, dashboard e relatório, além de VIEWER negado e isolamento entre tenants.
10. O contrato prático e o inventário completo de endpoints tornam-se documentação obrigatória para Portal/Flutter.

## Freeze do Backend MVP

Com a Phase 11 concluída, o Backend MVP é considerado completo. Não adicionaremos funcionalidades por antecipação. Um novo endpoint ou comportamento só deve surgir por:

- necessidade concreta do Portal ou Flutter;
- bug, risco de segurança ou gap de contrato descoberto na integração;
- requisito de produto posteriormente aprovado.

## Consequências

O backend tem uma fronteira operacional e contratual clara, com custo pequeno de springdoc e testes adicionais. A documentação em produção exige opt-in consciente. Probes podem ser usados por orquestradores sem expor configuração. A regressão leva mais tempo porque sobe PostgreSQL real, em troca de provar migrations, RLS e integrações.

Algumas otimizações ficam orientadas por telemetria: contagens dedicadas para inventário/finanças, paginação de catálogos caso cresçam e scanner Maven/CVE alinhado à futura plataforma de segurança.
