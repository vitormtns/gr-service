# ADR 0027 — Manejo de peso e sanidade do rebanho

## Estado

Aceita para a Phase 09A.

## Decisão

Pesagens e manejos de vacinação ou vermifugação são fatos imutáveis em tabelas próprias, com eventos tipados na timeline pública `app.animal_events`. Cada comando toca a versão do animal, exige `operationId` e usa payload canônico para replay idempotente. Lotes são transacionais e limitados a 100 animais.

## Consequências

Consultas históricas permanecem eficientes sem duplicar a timeline. Produto, protocolo e próxima data são descritivos; não há integração com Inventory, catálogo clínico ou agenda nesta fase.
