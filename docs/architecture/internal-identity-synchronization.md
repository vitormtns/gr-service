# Sincronização da identidade interna

## Fluxo

```text
Supabase Auth
  -> access token JWT
  -> Spring Security Resource Server
  -> AuthenticatedUser
  -> SynchronizeAuthenticatedUser
  -> InternalUserRepository
  -> app.users
```

O Resource Server autentica e valida o token sem abrir transação. A sincronização ocorre somente no caso de uso chamado por `GET /api/v1/me`; não existe filtro global que grave no banco em toda requisição.

O UUID validado de `sub` é usado diretamente como `app.users.id`. Não existe FK para `auth.users`, SDK administrativo, senha, bearer token ou refresh token no modelo persistido. E-mail é opcional: somente um valor não vazio com formato e tamanho válidos pode atualizar o registro, enquanto ausência ou valor inválido preserva o dado atual. O contrato JWT atual não define uma fonte confiável para nome de exibição, por isso `display_name` não é alterado pela sincronização.

## Idempotência e concorrência

A primeira escrita usa a PK natural de `sub` e `ON CONFLICT (id) DO NOTHING`. O processo concorrente que não inseriu relê o mesmo usuário. Atualizações reais incluem a versão esperada no `WHERE`, incrementam `version` e mudam `updated_at`; chamadas sem mudança não executam update. Conflitos de versão fazem no máximo três tentativas com releitura, sem lock distribuído ou estado em memória.

Os estados `SUSPENDED` e `DEACTIVATED` nunca são convertidos em `ACTIVE` por um JWT válido. Eles interrompem o fluxo com `403` e códigos de erro estáveis.

## Acesso PostgreSQL

O `DataSource` conecta com um login runtime dedicado, `NOINHERIT`, sem privilégios diretos e membro de `app_api`. Cada operação do repository exige uma transação Spring e executa `SET LOCAL ROLE app_api` na mesma conexão antes do SQL qualificado em `app.users`. A role continua `NOLOGIN`, sem superusuário e sem `BYPASSRLS`.

`app.users` é global e não exige contexto de tenant. Organização, fazenda, membership, `TenantContext` e `app.current_tenant_id` permanecem fora desta etapa.
