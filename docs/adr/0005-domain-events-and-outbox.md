# ADR 0005 — Eventos de domínio e outbox

## Estado da decisão

Aceita como direção; persistência adiada até o primeiro caso de integração.

## Contexto

Mudanças futuras poderão alimentar notificações, auditoria, sincronização e outros módulos. Publicar fora da transação cria risco de salvar o negócio sem registrar a mensagem, ou o inverso.

## Decisão

Eventos de domínio terão metadados e versão explícitos. Quando houver publicação assíncrona real, a mensagem de integração será persistida em outbox na mesma transação da mudança e publicada por worker idempotente.

## Consequências positivas

- Reduz perda de mensagens entre banco e transporte.
- Fornece contratos evolutivos e rastreáveis por correlation/causation ID.

## Consequências negativas

- Entrega será pelo menos uma vez, exigindo consumidores idempotentes.
- Outbox precisa de retenção, monitoramento e tratamento de falhas.

## Alternativas consideradas

- Publicação direta após commit: janela de perda inaceitável em operações críticas.
- Transação distribuída: complexa e pouco suportada pela stack prevista.
- Persistir todos os eventos agora: rejeitado por ausência de consumidor.
