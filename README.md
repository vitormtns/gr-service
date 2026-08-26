# gr-service

Backend do projeto provisoriamente chamado **Gerenciador Rural**. Este repositório contém a fundação técnica de uma API SaaS multi-tenant, organizada como monólito modular e preparada para crescer sem antecipar infraestrutura distribuída.

## Estado atual

A aplicação Spring Boot é executável e expõe apenas `GET /actuator/health`. Existem contratos mínimos para comandos, consultas, eventos de domínio e contexto de tenant, além de identificação de requisições e uma convenção de erros de validação. Módulos de negócio, autenticação, organizações, fazendas, usuários e persistência ainda não foram implementados.

## Arquitetura resumida

- Monólito modular orientado por domínio, com limites verificados por ArchUnit.
- Separação lógica de escrita e leitura, sem command bus ou query bus.
- Tenant definido pela futura `Organization`; uma organização poderá ter várias fazendas.
- API stateless, pronta para múltiplas instâncias e workers futuros.
- PostgreSQL gerenciado por migrations do Supabase CLI, sem alteração automática de schema.
- Eventos de domínio versionados, com outbox e Event Sourcing apenas como decisões futuras e seletivas.

Leia [a visão arquitetural](docs/architecture/overview.md) e os [ADRs](docs/adr/) antes de alterar decisões estruturais.

## Requisitos

- Java 21;
- acesso à internet na primeira execução do Maven Wrapper;
- Node.js e npm para as ferramentas locais de infraestrutura;
- Docker Desktop com backend WSL 2 para operar o PostgreSQL local.

Não é necessário instalar Maven nem Supabase CLI globalmente. O Wrapper usa Maven 3.9.16 e a CLI do Supabase está fixada como `devDependency` no `package.json`. A API ainda não abre conexão com o banco, portanto pode ser executada sem Docker durante esta etapa.

## Preparação do ambiente

1. Copie `.env.example` para `.env` somente se precisar documentar valores locais. O Spring não carrega `.env` automaticamente.
2. Execute `npm ci` para instalar a versão exata das ferramentas registrada em `package-lock.json`.
3. Defina `SPRING_PROFILES_ACTIVE=local` no ambiente.
4. Quando precisar do banco local, inicie o Docker Desktop e execute `npm run supabase:start`.

Variáveis documentadas em `.env.example`:

| Variável | Finalidade |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Seleciona o perfil Spring; use `local` no desenvolvimento. |
| `SERVER_PORT` | Porta HTTP, com padrão local `8080`. |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Conexão futura com o PostgreSQL; ainda não são consumidas. |
| `SUPABASE_URL`, `SUPABASE_JWT_ISSUER` | Integração futura com Supabase Auth; deixe vazias nesta fundação. |

Nunca versione `.env`, senhas, tokens, `service_role` ou chaves secretas.

## Execução local

No PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

Em Bash:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Verifique a aplicação em `http://localhost:8080/actuator/health`. O Actuator expõe somente o endpoint de saúde e não mostra detalhes publicamente.

## Testes e validação arquitetural

No Windows:

```powershell
.\mvnw.cmd verify
.\scripts\check.ps1
```

Em Bash:

```bash
./mvnw verify
./scripts/check.sh
```

O mesmo comando compila a aplicação, executa JUnit 5, valida os limites com ArchUnit e empacota o JAR. Testcontainers PostgreSQL será adicionado somente quando houver um teste de integração real com banco.

## Supabase local e migrations

Com Docker Desktop em execução e as dependências instaladas por `npm ci`:

```bash
npm run supabase:version
npm run supabase:start
npm run supabase:status
npm run supabase:reset
npm run supabase:stop
npx supabase migration new nome_da_migration
```

Detalhes estão em [supabase/README.md](supabase/README.md). Toda alteração estrutural deve ser feita por migration versionada. Não altere tabelas de negócio manualmente pelo painel e não use `supabase db push` sem um fluxo de entrega aprovado. Nenhuma migration ornamental foi criada nesta fundação.

## Estrutura

```text
src/main/java/com/gerenciadorrural/
├── GerenciadorRuralApplication.java
├── modules/                 # módulos de negócio futuros
└── shared/
    ├── api/                 # convenções HTTP compartilhadas
    ├── application/         # contratos de command e query
    ├── domain/              # conceitos de domínio transversais
    ├── infrastructure/      # adaptadores técnicos compartilhados
    ├── observability/       # contexto de diagnóstico
    └── tenancy/             # identidade e contexto do tenant
docs/                        # arquitetura, ADRs e roadmap
supabase/                    # configuração, migrations e seed local
scripts/                     # validação para Windows e Bash
```

Cada módulo futuro seguirá `modules/<module>/{domain,application,infrastructure,api}`, com `application/command` e `application/query` quando houver casos de uso. Não crie módulos ou camadas vazias apenas para completar a árvore.

## Banco e acesso ao domínio

O schema pertence às migrations do Supabase CLI. JPA não foi incluído porque ainda não existem entidades; quando houver uso real, `ddl-auto` deverá ser `validate`, nunca `update`, `create` ou `create-drop`.

Flutter e Angular não poderão escrever diretamente nas tabelas de negócio. Clientes chamam a API, que autentica, resolve usuário e tenant, valida fazenda, permissões, capacidades e cotas. RLS será uma defesa complementar, não um substituto das regras da API.
