# ADR 0014 — Identidade interna desacoplada do Supabase Auth

## Estado da decisão

Aceita.

## Contexto

O UUID do claim `sub` do Supabase Auth identifica a pessoa autenticada, mas as regras de negócio não devem depender das tabelas internas do provedor. Os testes de migration também precisam funcionar em PostgreSQL 15 padrão, sem o schema `auth`.

## Decisão

`app.users.id` aceitará o mesmo UUID recebido no claim `sub`, sem foreign key para `auth.users`. A próxima etapa sincronizará a identidade autenticada com o usuário interno pela API. A tabela da aplicação não armazenará senha, token ou chave administrativa do Supabase.

O usuário é global e pode possuir memberships em várias organizações. Organização e fazenda não serão propriedades diretas da identidade.

## Consequências positivas

- O domínio permanece independente da estrutura interna do Supabase.
- As migrations são executáveis em PostgreSQL padrão e no ambiente local do Supabase.
- Outros provedores de identidade podem ser incorporados sem remodelar o tenant.

## Consequências negativas

- A aplicação precisará sincronizar e validar explicitamente o usuário autenticado.
- Integridade entre o provedor e `app.users` será responsabilidade do fluxo de identidade, não de uma FK.

## Alternativas consideradas

- FK direta para `auth.users`: rejeitada pelo acoplamento ao provedor e pela incompatibilidade com PostgreSQL padrão.
- UUID interno diferente do `sub`: adiado por exigir um mapeamento sem benefício atual.
- Armazenar credenciais na aplicação: rejeitado por segurança e duplicação de responsabilidade.
