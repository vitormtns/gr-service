# ADR 0004 — CQRS lógico

## Estado da decisão

Aceita.

## Contexto

Escritas rurais exigirão invariantes; telas e relatórios terão formatos de leitura próprios. Ainda não há necessidade de infraestrutura de mensageria ou bancos separados.

## Decisão

Separar casos de uso por `Command`/`CommandHandler` e `Query`/`QueryHandler`, com chamadas diretas no mesmo processo. Não criar buses, registries, descoberta por reflexão ou modelo de leitura separado antecipadamente.

## Consequências positivas

- Intenção de cada caso de uso fica explícita.
- Leituras podem evoluir sem contaminar regras de alteração.

## Consequências negativas

- Há mais tipos e pacotes que em um serviço CRUD simples.
- A separação lógica não oferece escala independente por si só.

## Alternativas consideradas

- Serviços CRUD únicos: simples, mas mistura invariantes e consultas.
- CQRS distribuído completo: rejeitado por complexidade sem consumidor.
- Framework interno de dispatch: rejeitado por esconder dependências diretas.
