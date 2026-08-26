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

Analytics/Vector permanece desativado nesta fundação. No Windows, a coleta local de logs exigiria expor o daemon Docker em `tcp://localhost:2375`; essa superfície administrativa não é necessária para o banco e a API locais.

Crie uma migration com um nome descritivo:

```bash
npx supabase migration new nome_da_migration
```

Edite o SQL gerado em `migrations/` e valide-o com `npm run supabase:reset`. Mudanças estruturais devem sempre passar por migration; não replique no repositório alterações feitas manualmente pelo painel. Não use `supabase link` ou `supabase db push` no fluxo local da fundação.

O `seed.sql` é exclusivo para dados locais reproduzíveis. Nunca inclua credenciais, tokens ou dados pessoais reais.
