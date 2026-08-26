# Escalabilidade

## Princípios

A API deve operar sem afinidade de sessão e sem estado funcional na JVM ou em arquivos locais. Isso permite executar múltiplas instâncias atrás de um load balancer e escalar verticalmente enquanto for mais simples.

Trabalho pesado ou demorado será representado por jobs persistidos e executado por workers separados. Retries precisam ser idempotentes e observáveis. Um lock distribuído só será adotado quando coordenação exclusiva for inevitável; concorrência normal deve preferir constraints e locking otimista no PostgreSQL.

O banco principal poderá receber réplicas de leitura quando métricas indicarem necessidade e a tolerância a consistência eventual estiver definida por consulta. Cache futuro será externo e reconstruível, nunca fonte de verdade.

## Evolução

O `tenant_id` deve aparecer em índices e consultas operacionais. Ele poderá se tornar chave de particionamento, distribuição de filas ou sharding, mas somente após análise de volume e distribuição dos tenants. Evite decisões irreversíveis baseadas em escala hipotética.

Módulos poderão ser extraídos como serviços quando houver fronteira estável, necessidade independente de escala ou autonomia operacional que compense consistência distribuída e maior custo. Até lá, chamadas locais e uma única transação são preferíveis.

## Operação

Propague request ID, correlation ID e contexto validado de tenant. Não registre dados sensíveis. Métricas, tracing externo, autoscaling, cache, brokers e orquestração não fazem parte desta fundação.
