# ADR 0006 — Event Sourcing seletivo

## Estado da decisão

Aceita como restrição: não é padrão e exige ADR por agregado.

## Contexto

Alguns históricos rurais podem futuramente se beneficiar de reconstrução temporal. A maioria dos cadastros não justifica snapshots, upcasting e operação de um event store.

## Decisão

Usar persistência de estado convencional por padrão. Event Sourcing só poderá ser adotado em agregado específico quando histórico completo, reconstrução ou regras temporais trouxerem benefício mensurável.

## Consequências positivas

- Evita custo sistêmico para módulos CRUD ou transacionais comuns.
- Mantém aberta uma opção para domínios com necessidade histórica forte.

## Consequências negativas

- O sistema poderá conviver com dois modelos de persistência.
- Decisões tardias podem exigir migração de histórico disponível.

## Alternativas consideradas

- Event Sourcing global: rejeitado por complexidade prematura.
- Proibir Event Sourcing: rejeitado porque pode haver agregado com benefício real.
- Tratar auditoria como Event Sourcing: rejeitado; auditoria não reconstrói invariantes do agregado.
