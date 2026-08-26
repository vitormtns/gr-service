# Visão arquitetural

## Contexto

`gr-service` será a fonte das regras de negócio do Gerenciador Rural para portal web e aplicativo móvel. O sistema nasce como SaaS multi-tenant: a futura `Organization` será o tenant e poderá agrupar várias fazendas.

## Forma da aplicação

A API é um monólito modular Spring Boot. Cada capacidade de negócio será um módulo vertical com domínio, aplicação, infraestrutura e API próprios. O pacote `shared` contém apenas conceitos transversais estáveis; ele não é um depósito de utilitários.

O fluxo esperado é:

```text
HTTP -> API do módulo -> command/query handler -> domínio -> porta
                                                    |
                                             adaptador técnico
```

Comandos alteram estado; consultas leem projeções ou modelos adequados. A separação é lógica e ocorre no mesmo processo. Não há bus, broker ou banco separado de leitura.

## Tenancy e segurança

O cliente nunca é autoridade sobre o tenant. O fluxo futuro será:

```text
Supabase Auth
  -> JWT
  -> Spring Security Resource Server
  -> resolução do usuário
  -> resolução e validação do tenant
  -> validação da fazenda
  -> permissões
  -> capacidades e cotas do plano
```

A chave pública do Supabase poderá existir no portal e no aplicativo. `service_role` ou qualquer chave secreta ficará somente em backend seguro. Autorização não será delegada ao frontend. RLS será defesa complementar às regras da API.

## Dados e integração

O PostgreSQL é compartilhado entre tenants, com isolamento por `tenant_id`. Tabelas de negócio ficam no schema privado `app`, que não é exposto pelo PostgREST. Constraints compostas impedem relações cruzadas e a role `app_api` está sujeita a RLS baseada em contexto transacional. Repositories futuros ainda deverão filtrar explicitamente pelo tenant validado.

Usuários internos são globais e podem participar de várias organizações. Seu UUID corresponderá ao claim `sub`, sem FK para `auth.users`; sincronização e autenticação serão implementadas na próxima etapa. O schema é controlado por migrations do Supabase CLI e validado em PostgreSQL 15 com Testcontainers.

Eventos de domínio poderão originar mensagens de outbox na mesma transação da alteração; publicação assíncrona e consumidores serão adicionados apenas quando existir um caso real.

A aplicação permanece stateless. Request ID e correlation ID entram no contexto de logs; tenant ID será incluído quando a autenticação fornecer um contexto validado. Logs não devem conter JWT, segredos ou dados pessoais desnecessários.

## Limites desta fundação

Existem apenas o modelo SQL de identidade/tenancy e seus testes de integração. Não existem autenticação HTTP, Spring Security, JPA, repositories, casos de uso, endpoints, outbox, jobs, idempotência persistida ou Event Sourcing.
