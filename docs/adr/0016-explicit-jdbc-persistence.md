# ADR 0016 — Persistência JDBC explícita

## Estado da decisão

Aceita.

## Contexto

A API precisa sincronizar a identidade global em `app.users` e, na etapa seguinte, configurar role e contexto de tenant na mesma transação PostgreSQL. O schema continua controlado exclusivamente pelas migrations do Supabase CLI.

## Decisão

Usar Spring JDBC, `NamedParameterJdbcTemplate`, transações Spring, SQL qualificado e mapeamento manual nos repositories. O login runtime não possui privilégios diretos e assume `app_api` com `SET LOCAL ROLE` dentro de cada transação. JPA, Hibernate, repositories automáticos e geração de schema não serão adotados como padrão.

## Consequências positivas

- SQL, schema, quantidade de linhas afetadas, locking e contexto transacional permanecem explícitos.
- Não existe estado oculto de ORM nem outro proprietário das migrations.
- O mesmo desenho permite adicionar o tenant validado na próxima etapa.

## Consequências negativas

- Mapeamento e SQL exigem código manual e testes de integração.
- Evoluções de coluna precisam atualizar migration, modelo, mapper e consultas de forma coordenada.

## Alternativas consideradas

- JPA/Hibernate: rejeitado pelo estado implícito e pela menor visibilidade sobre role, transação e SQL nesta fase.
- Biblioteca de SQL adicional: rejeitada porque o primeiro repository não justifica outra abstração.
- Acesso com superusuário ou `service_role`: rejeitado por privilégios excessivos.
