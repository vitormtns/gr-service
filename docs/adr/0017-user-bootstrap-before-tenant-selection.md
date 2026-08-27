# ADR 0017 — Bootstrap transacional de organizações antes da seleção de tenant

## Estado da decisão

Aceita.

## Contexto

As policies RLS de `app.organizations` e `app.organization_memberships` dependem de `app.current_tenant_id()`. Antes de um tenant ser selecionado, a API precisa descobrir as organizações ativas às quais a pessoa autenticada já possui acesso. Uma consulta direta como `app_api` não pode atravessar essas policies, e receber um `userId` do cliente abriria uma falha de isolamento.

## Decisão

O backend configura `app.current_user_id` somente com `set_config(..., true)` dentro da transação que processa a requisição. A função `app.current_user_id()` lê esse valor e retorna `null` quando ele está ausente.

`app.list_current_user_organizations()` é uma função SQL `SECURITY DEFINER`, sem argumentos e com `search_path` vazio. Ela consulta exclusivamente o usuário presente no contexto transacional, retorna somente organizações e memberships ativos, e não retorna fazendas. `PUBLIC`, `anon` e `authenticated` não têm execução; somente `app_api` recebe o grant explícito.

O caso de uso sincroniza a identidade autenticada antes de configurar o contexto e executar a leitura, tudo na mesma transação Spring e conexão JDBC. O tenant continua não selecionado nesta etapa.

## Consequências positivas

- Permite a descoberta segura de tenants sem enfraquecer ou remover RLS.
- O UUID é proveniente da identidade JWT já validada pelo Resource Server, não de entrada HTTP.
- `SET LOCAL` e `set_config(..., true)` eliminam o contexto no commit ou rollback, inclusive quando conexões são reutilizadas no pool.
- A função tem superfície pequena, somente leitura e projeção limitada.

## Consequências negativas

- A função `SECURITY DEFINER` exige revisão cuidadosa de grants, `search_path` e corpo SQL em cada alteração.
- Uma futura seleção de tenant ainda precisará validar membership e contexto de fazenda separadamente.

## Alternativas consideradas

- Desativar RLS ou remover `FORCE ROW LEVEL SECURITY`: rejeitado por reduzir a defesa em profundidade.
- Executar com superusuário, `BYPASSRLS` ou `service_role`: rejeitado por privilégio excessivo.
- Receber o UUID do usuário como argumento da função: rejeitado porque permitiria tentar enumerar usuários.
- Criar um `TenantContext` antes da listagem: rejeitado porque o tenant ainda não foi escolhido.
