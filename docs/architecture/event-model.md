# Modelo de eventos

## Contrato de domínio

Um evento de domínio representa um fato ocorrido e é imutável. O contrato base inclui identificador, tipo, versão, instante UTC, agregado e versão, tenant, fazenda/usuário/dispositivo opcionais, correlation ID e causation ID. Eventos concretos pertencem ao módulo que define o fato.

## Conceitos distintos

- **Evento de domínio:** linguagem interna do domínio, produzido durante uma mudança válida. Pode ser consumido localmente.
- **Evento de integração:** contrato público e estável enviado a outro módulo ou sistema. Pode ser derivado de um evento de domínio, com payload minimizado e versionado.
- **Registro de auditoria:** evidência de quem fez o quê e quando. Serve investigação e conformidade; não dirige regras de negócio.
- **Mensagem da outbox:** registro técnico persistido na mesma transação da mudança, usado para publicação confiável e com estado de entrega.
- **Evento armazenado por Event Sourcing:** fonte de verdade histórica de um agregado event-sourced. Reconstrói estado e exige regras próprias de evolução; não é sinônimo de evento de domínio comum.

## Regras de evolução

Tipos e versões são explícitos. Consumidores devem ser idempotentes. Mudança incompatível gera nova versão, com estratégia de convivência. Correlation ID liga uma operação ponta a ponta; causation ID aponta para a mensagem ou evento que causou o fato.

Esta fundação não persiste nem publica eventos. Outbox será implementada junto do primeiro caso real de integração. Event Sourcing exige decisão específica por agregado e não será padrão do sistema.
