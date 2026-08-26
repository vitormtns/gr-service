# ADR 0010 — Jobs em segundo plano persistidos

## Estado da decisão

Aceita como direção; implementação adiada.

## Contexto

Relatórios, importações, sincronizações e publicação de outbox poderão ultrapassar o tempo seguro de uma requisição HTTP. Tarefas somente em memória se perdem em reinícios.

## Decisão

Quando houver trabalho assíncrono real, representar sua intenção e estado em armazenamento durável. Workers separados buscarão jobs, renovarão ownership quando necessário e executarão handlers idempotentes com retries limitados.

## Consequências positivas

- Trabalho sobrevive a deploys e falhas de processo.
- API e workers podem escalar com perfis diferentes.

## Consequências negativas

- Exige política de retry, timeout, cancelamento e fila de falhas.
- A conclusão passa a ser eventual e precisa de consulta de estado.

## Alternativas consideradas

- `@Async` sem persistência: rejeitado para trabalho crítico.
- Executar tudo na requisição: aumenta timeout e acoplamento.
- Instalar broker agora: rejeitado antes de existir carga ou requisito.
