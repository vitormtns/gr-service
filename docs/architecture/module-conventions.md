# Convenções de módulos

Um módulo representa uma capacidade coesa, não uma entidade ou camada técnica global.

```text
modules/<module>/
├── domain/
├── application/
│   ├── command/
│   └── query/
├── infrastructure/
└── api/
```

- `domain`: agregados, value objects, políticas, eventos e portas que expressam o negócio. Não depende de Spring, HTTP ou persistência.
- `application`: casos de uso. Commands expressam intenção de escrita e queries expressam leitura. Handlers são chamados diretamente.
- `infrastructure`: adaptadores de banco, filas e serviços externos. Implementa portas pertencentes ao módulo.
- `api`: controllers, DTOs, validação de entrada e tradução de erros HTTP.

O pacote `shared` aceita apenas elementos realmente transversais, como `TenantId`, metadados de eventos e contratos de command/query. Uma classe usada por dois módulos não se torna automaticamente compartilhada.

## Dependências permitidas

`api -> application -> domain`; `infrastructure -> application/domain`. O domínio não aponta para as demais camadas. Integrações entre módulos usam APIs públicas de aplicação ou eventos, nunca tabelas ou repositórios internos.

Não crie diretórios vazios. Materialize uma camada somente quando existir código com responsabilidade concreta. Novas regras estruturais devem ser expressas em ArchUnit quando puderem ser verificadas automaticamente.
