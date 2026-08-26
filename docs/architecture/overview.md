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

O PostgreSQL será compartilhado entre tenants, com isolamento por `tenant_id`. O schema será controlado por migrations do Supabase CLI. Eventos de domínio poderão originar mensagens de outbox na mesma transação da alteração; publicação assíncrona e consumidores serão adicionados apenas quando existir um caso real.

A aplicação permanece stateless. Request ID e correlation ID entram no contexto de logs; tenant ID será incluído quando a autenticação fornecer um contexto validado. Logs não devem conter JWT, segredos ou dados pessoais desnecessários.

## Limites desta fundação

Não existem módulos de negócio, autenticação, JPA, tabelas, outbox, jobs, idempotência persistida ou Event Sourcing. Os contratos atuais estabelecem linguagem e limites, não implementam essas capacidades antecipadamente.
