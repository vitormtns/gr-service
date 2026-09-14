# Guia de consumo da API

Este guia é o contrato prático para o Portal Angular e o aplicativo Flutter. A especificação executável fica em `/v3/api-docs/api-v1`; no perfil `local`, a interface Swagger UI fica em `/swagger-ui.html`. Em produção, ambas permanecem desabilitadas por padrão.

## Autenticação e seleção de contexto

Envie o access token em todas as rotas `/api/v1/**`:

```http
Authorization: Bearer <token>
```

O backend valida assinatura, algoritmo, emissor, audience, validade temporal, role técnica e o UUID de `sub`. O token identifica o usuário, mas não concede papel de negócio. Papéis e escopos sempre vêm do banco.

O fluxo inicial recomendado é:

1. `GET /api/v1/me` para sincronizar e ler a identidade interna;
2. `GET /api/v1/me/organizations` para listar organizações acessíveis;
3. `GET /api/v1/me/organizations/{organizationId}/farms` para listar fazendas acessíveis;
4. `GET /api/v1/context`, com os dois headers abaixo, para confirmar o contexto;
5. manter os mesmos headers nas rotas tenant-aware.

```http
X-Organization-Id: 2a7fcb65-c4f8-47af-a903-9b8d30d73e42
X-Farm-Id: b0a2164b-45e1-4915-bec1-93c83261515a
```

Os valores são UUIDs solicitados pelo cliente, não autorização. O backend revalida usuário, membership, papel, fazenda, escopo e estados atuais em cada requisição. Headers ausentes ou inválidos geram `400`; contexto inacessível gera `404` sem confirmar se o recurso existe. Rotas de bootstrap e administração de organizações que não usam a fazenda não exigem esses headers.

## Escritas, repetição e concorrência

Comandos que possuem `operationId` são idempotentes. Gere um UUID uma única vez e preserve o mesmo ID e o mesmo comando em todas as tentativas até receber confirmação. Nunca gere outro `operationId` apenas porque houve timeout.

Algumas respostas incluem `replay: true`: a operação já havia sido processada e a resposta anterior foi recuperada. O cliente pode tratá-la como sucesso confirmado. Reutilizar o ID com conteúdo diferente retorna `409`.

Recursos mutáveis usam `version` e comandos de correção usam `expectedVersion`. Envie a versão que foi lida. Em `409`, recarregue o estado mais recente e faça reconciliação; não repita cegamente uma alteração baseada em versão antiga.

Exemplo de criação de animal:

```http
POST /api/v1/herd/animals
Content-Type: application/json
Authorization: Bearer <token>
X-Organization-Id: 2a7fcb65-c4f8-47af-a903-9b8d30d73e42
X-Farm-Id: b0a2164b-45e1-4915-bec1-93c83261515a

{
  "id": "d31ad9b5-fc8d-4e55-8a49-d7ca5ae2cae2",
  "identification": "BR-2026-001",
  "name": "Aurora",
  "sex": "FEMALE",
  "birthDate": "2024-03-15"
}
```

Exemplo de movimento de entrada no estoque:

```json
{
  "operationId": "91bd35a8-5c90-4dfc-8cf8-2efb7c46f397",
  "type": "RECEIPT",
  "productId": "9e6b07c3-5b8c-4654-af2d-428d04b72cdb",
  "destinationLocationId": "e9749e1f-510a-4261-a09e-978465ebc934",
  "quantity": 25,
  "occurredOn": "2026-09-14"
}
```

Exemplo de lançamento financeiro:

```json
{
  "operationId": "4259cbd8-9014-4147-a31c-9483ff1cab92",
  "type": "INCOME",
  "categoryId": "22e70fd5-a56d-478d-ad7f-911c5831f74c",
  "description": "Venda do lote",
  "amount": 1200.00,
  "dueOn": "2026-09-14"
}
```

Exemplo de item do planejamento:

```json
{
  "operationId": "848ad781-b38b-423d-94a1-0f1fbfa8d742",
  "type": "VACCINATION",
  "title": "Vacinação do lote",
  "scheduledFor": "2026-09-15"
}
```

## Paginação, datas e números

Listagens paginadas usam `page` zero-based e `size`, com limite máximo documentado no OpenAPI. A resposta estável é:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Listas pequenas de referência, como categorias, produtos, locais e opções de contexto, retornam coleção sem paginação. Coleção vazia é `[]`, nunca `null`. A ordenação é estável e inclui um identificador como desempate.

- Datas civis usam `YYYY-MM-DD` (`LocalDate`).
- Instantes usam ISO 8601 em UTC, por exemplo `2026-09-14T21:30:00Z`.
- IDs usam UUID textual.
- Dinheiro, pesos e quantidades são decimais exatos; não converta para ponto flutuante quando isso puder perder precisão.
- Enums são strings em maiúsculas conforme o OpenAPI.

## Erros

O frontend pode interpretar todo erro de API pelo mesmo envelope:

```json
{
  "code": "validation_error",
  "message": "Existem campos inválidos na solicitação",
  "status": 400,
  "requestId": "5a4f949f-6c26-464c-b98b-2dd97ca2cfc1",
  "validationErrors": [
    { "field": "name", "message": "não deve estar em branco" }
  ],
  "timestamp": "2026-09-14T21:30:00Z"
}
```

- `400`: JSON, query, header ou regra de entrada inválida;
- `401`: token ausente, malformado, expirado ou inválido;
- `403`: identidade válida sem permissão para a ação;
- `404`: recurso/contexto indisponível; também é usado para não enumeração;
- `409`: conflito de negócio, idempotência ou concorrência otimista;
- `500`: falha inesperada sanitizada;
- `503`: persistência/dependência temporariamente indisponível.

Não dependa apenas de `message`; use `status` e `code`. Envie `X-Request-ID` ou `X-Correlation-ID` opcionalmente com 1 a 128 caracteres alfanuméricos, ponto, sublinhado ou hífen. O backend devolve ambos os IDs; valores inseguros são substituídos.

## Regras para clientes offline-first

- O backend é a fonte de verdade; Portal e Flutter nunca acessam tabelas do Supabase diretamente.
- Preserve o comando local e seu `operationId` até confirmação.
- `replay=true` confirma que o mesmo comando já foi aplicado.
- Um `409` exige reconciliação de estado ou decisão do usuário.
- Após reconciliação, uma nova intenção pode receber novo `operationId`; um simples retry não.
- Não infira acesso pela posse de IDs. O contexto sempre é revalidado no servidor.
- Não existe engine de sincronização genérica no MVP; a fila offline pertence ao cliente e deve respeitar os contratos por comando.

Consulte o [inventário completo de endpoints](endpoint-inventory.md) para a superfície atual da API.
