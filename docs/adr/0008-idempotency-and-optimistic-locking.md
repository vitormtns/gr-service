# ADR 0008 — Idempotência e locking otimista

## Estado da decisão

Aceita como política; implementação será feita por caso de uso.

## Contexto

Redes móveis e sincronização offline podem repetir comandos. Alterações concorrentes no mesmo agregado não devem sobrescrever dados silenciosamente.

## Decisão

Operações críticas definirão chave e escopo de idempotência, armazenarão resultado ou estado de processamento e tratarão retries. Agregados mutáveis usarão versão para locking otimista quando houver concorrência relevante. Constraints do banco protegerão invariantes simples.

## Consequências positivas

- Retries de cliente e workers tornam-se seguros.
- Conflitos são detectados em vez de causar perda silenciosa.

## Consequências negativas

- Chaves exigem retenção, limpeza e semântica clara.
- O cliente pode precisar resolver respostas de conflito.

## Alternativas consideradas

- Confiar que o cliente não repita: incompatível com redes instáveis.
- Lock pessimista global: reduz concorrência e escala mal.
- Lock distribuído em toda escrita: rejeitado por custo e fragilidade desnecessários.
