# ADR 0027 — Manejo de peso e sanidade do rebanho

## Estado

Aceita para a Phase 09A.

## Decisão

Pesagens e manejos de vacinação ou vermifugação são fatos imutáveis em tabelas próprias, com eventos tipados na timeline pública `app.animal_events`. Cada comando toca a versão do animal, exige `operationId` e usa payload canônico para replay idempotente. Lotes são transacionais e limitados a 100 animais.

## Consequências

Pesagens validam também os dígitos inteiros permitidos por `NUMERIC(8,3)`, com máximo de 99.999,999 kg. Registros de leite respeitam `NUMERIC(9,3)`, com máximo de 999.999,999 L. A validação considera números sem fração e em notação científica, antes da transação, para evitar falhas de armazenamento. Identificadores dos lotes são validados antes da ordenação dos comandos; valores nulos são entrada inválida.

Consultas históricas permanecem eficientes sem duplicar a timeline. Produto, protocolo e próxima data são descritivos; não há integração com Inventory, catálogo clínico ou agenda nesta fase.
