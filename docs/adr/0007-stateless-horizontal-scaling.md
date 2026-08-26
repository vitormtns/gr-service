# ADR 0007 — Escala horizontal stateless

## Estado da decisão

Aceita.

## Contexto

A API poderá executar em várias instâncias e receber requisições de web e mobile, inclusive com retries. Estado mantido em processo criaria afinidade e divergência entre instâncias.

## Decisão

Não manter estado funcional em memória da JVM, sessão HTTP, singleton mutável ou arquivo local. Estado durável fica em armazenamento compartilhado; jobs persistidos serão processados por workers separados quando necessários.

## Consequências positivas

- Instâncias podem ser adicionadas, removidas ou reiniciadas atrás de load balancer.
- Falhas de processo não removem estado de negócio persistido.

## Consequências negativas

- Coordenação e caches futuros precisam de soluções externas.
- Todas as requisições devem carregar ou resolver contexto suficiente.

## Alternativas consideradas

- Sessão sticky: rejeitada por acoplamento operacional e recuperação ruim.
- Estado local replicado: rejeitado pela complexidade de consistência.
- Uma única instância permanente: não atende disponibilidade e crescimento previstos.
