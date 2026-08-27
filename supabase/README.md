# PostgreSQL local com Supabase CLI

Este diretório versiona a configuração e as migrations do banco. A CLI 2.115.0 é uma `devDependency` local fixada por `package.json` e `package-lock.json`; não instale a CLI globalmente. A configuração foi validada com Docker Desktop e backend WSL 2. Nenhum projeto remoto foi vinculado ou alterado.

## Fluxo local

Instale as ferramentas e controle a stack pelos scripts npm:

```bash
npm ci
npm run supabase:version
npm run supabase:start
npm run supabase:status
npm run supabase:reset
npm run supabase:stop
```

Os serviços locais expõem a API em `127.0.0.1:54321`, PostgreSQL em `127.0.0.1:54322`, Studio em `127.0.0.1:54323` e Mailpit em `127.0.0.1:54324`.

O Auth local validado nesta versão emite access tokens `ES256` com issuer `http://127.0.0.1:54321/auth/v1`, audience e role técnica `authenticated`. O endpoint `/auth/v1/.well-known/jwks.json` publica a chave pública necessária; a API usa esse JWKS no perfil `local`, sem ler o JWT secret legado exibido pela CLI. Não presuma que um projeto remoto usa o mesmo algoritmo: confira as signing keys do projeto e configure o modo explicitamente.

Analytics/Vector permanece desativado nesta fundação. No Windows, a coleta local de logs exigiria expor o daemon Docker em `tcp://localhost:2375`; essa superfície administrativa não é necessária para o banco e a API locais.

## Schema da aplicação

A migration `identity_tenancy_foundation` cria no schema privado `app`:

- usuários internos globais;
- organizações como tenants;
- fazendas;
- memberships organizacionais;
- escopos de acesso por fazenda;
- role restrita `app_api`;
- função transacional de tenant e policies RLS.

O schema `app` não aparece em `api.schemas` no `config.toml` e não é exposto pelo PostgREST. Não adicione policies para `anon` ou `authenticated`: o fluxo suportado é `Clientes -> API Spring -> PostgreSQL`.

`app.users.id` aceitará o UUID do claim `sub`, mas não há FK para `auth.users`. Senhas, tokens e chaves do Supabase nunca pertencem às tabelas da aplicação.

Crie uma migration com um nome descritivo:

```bash
npx supabase migration new nome_da_migration
```

Edite o SQL gerado em `migrations/` e valide-o com `npm run supabase:reset`. Mudanças estruturais devem sempre passar por migration; não replique no repositório alterações feitas manualmente pelo painel. Não use `supabase link` ou `supabase db push` no fluxo local da fundação.

`mvn verify` também aplica essas mesmas migrations, sem cópias, em PostgreSQL 15 iniciado pelo Testcontainers. A stack Supabase não precisa estar ativa para essa validação.

O `seed.sql` é exclusivo para dados locais reproduzíveis. Nunca inclua credenciais, tokens ou dados pessoais reais.
