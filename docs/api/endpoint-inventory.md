# Inventário de endpoints da API v1

Inventário revisado em 14/09/2026. Todos os endpoints abaixo exigem bearer JWT. Rotas marcadas com `CTX` também exigem `X-Organization-Id` e `X-Farm-Id`. `R` indica leitura por qualquer membership ativa (`OWNER`, `ADMIN`, `MANAGER`, `OPERATOR` ou `VIEWER`); as demais siglas indicam os papéis autorizados para escrita.

O envelope de erro comum contém `code`, `message`, `status`, `requestId`, `validationErrors` e `timestamp`. Respostas possíveis são `400`, `401`, `403`, `404`, `409`, `500` e `503` conforme a operação; o sistema não usa `422`. Escritas respondem com o recurso/resultante, exceto revogação de membership (`204`).

## Identidade, organizações e contexto

| Método | Caminho | Contexto/papel | Request → response |
| --- | --- | --- | --- |
| GET | `/api/v1/me` | autenticado | sem body → identidade atual |
| GET | `/api/v1/me/organizations` | autenticado | sem body → `{items}` de organizações acessíveis |
| GET | `/api/v1/me/organizations/{organizationId}/farms` | autenticado | path UUID → `{items}` de fazendas acessíveis |
| GET | `/api/v1/context` | CTX, R | headers → contexto autorizado |

## Plataforma, memberships e convites

| Método | Caminho | Papel | Request → response |
| --- | --- | --- | --- |
| POST | `/api/v1/organizations` | autenticado | id/nome → organização criada |
| GET | `/api/v1/organizations/{organizationId}` | membership | path UUID → organização |
| PATCH | `/api/v1/organizations/{organizationId}` | OWNER | nome/status/expectedVersion → organização |
| GET | `/api/v1/organizations/{organizationId}/farms` | membership | `page,size` → página de fazendas |
| POST | `/api/v1/organizations/{organizationId}/farms` | OWNER/ADMIN | id/nome → fazenda criada |
| GET | `/api/v1/organizations/{organizationId}/farms/{farmId}` | membership/escopo | path UUIDs → fazenda |
| PATCH | `/api/v1/organizations/{organizationId}/farms/{farmId}` | OWNER/ADMIN | nome/status/expectedVersion → fazenda |
| GET | `/api/v1/organizations/{organizationId}/members` | OWNER/ADMIN | `page,size` → página de memberships |
| POST | `/api/v1/organizations/{organizationId}/members` | OWNER/ADMIN | userId/papel/escopo → membership |
| PATCH | `/api/v1/organizations/{organizationId}/members/{membershipId}` | OWNER/ADMIN com proteção hierárquica | papel/escopo/expectedVersion → membership |
| DELETE | `/api/v1/organizations/{organizationId}/members/{membershipId}` | OWNER/ADMIN com proteção do último OWNER | `expectedVersion` → `204` |
| POST | `/api/v1/organizations/{organizationId}/invitations` | OWNER/ADMIN | e-mail/papel/escopo → convite e token de uso único |
| GET | `/api/v1/organizations/{organizationId}/invitations` | OWNER/ADMIN | `status,page,size` → página de convites |
| POST | `/api/v1/organizations/{organizationId}/invitations/{invitationId}/revocation` | OWNER/ADMIN | sem body → convite revogado |
| POST | `/api/v1/invitations/{token}/accept` | usuário convidado autenticado | token opaco no path → membership |
| GET | `/api/v1/organizations/{organizationId}/audit` | OWNER/ADMIN | `type,farmId,page,size` → página de auditoria |

## Fazenda atual

| Método | Caminho | Contexto/papel | Request → response |
| --- | --- | --- | --- |
| GET | `/api/v1/farms/current` | CTX, R | sem body → perfil da fazenda |
| PATCH | `/api/v1/farms/current` | CTX, OWNER/ADMIN/MANAGER | nome/expectedVersion → perfil atualizado |

## Rebanho, território e ciclo de vida

| Método | Caminho | Contexto/papel | Request → response |
| --- | --- | --- | --- |
| GET | `/api/v1/herd/animals` | CTX, R | `search,sex,status,page,size` → página de animais |
| POST | `/api/v1/herd/animals` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | comando estrito → animal; criação idempotente pelo `id` |
| GET | `/api/v1/herd/animals/{id}` | CTX, R | path UUID → animal |
| PATCH | `/api/v1/herd/animals/{id}` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | campos + expectedVersion → animal |
| POST | `/api/v1/herd/animals/{id}/sale` | CTX, OWNER/ADMIN/MANAGER | operationId/expectedVersion/data → animal vendido |
| POST | `/api/v1/herd/animals/{id}/death` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/expectedVersion/data → animal baixado |
| GET | `/api/v1/herd/animals/{id}/history` | CTX, R | `page,size` → página de eventos |
| POST | `/api/v1/herd/paddocks` | CTX, OWNER/ADMIN/MANAGER | id/nome/código → piquete |
| GET | `/api/v1/herd/paddocks` | CTX, R | `status,page,size` → página de piquetes |
| GET | `/api/v1/herd/paddocks/{id}` | CTX, R | path UUID → piquete |
| PATCH | `/api/v1/herd/paddocks/{id}` | CTX, OWNER/ADMIN/MANAGER | campos + expectedVersion → piquete |
| GET | `/api/v1/herd/paddocks/{id}/occupancy` | CTX, R | path UUID → ocupação resumida |
| GET | `/api/v1/herd/paddocks/{id}/animals` | CTX, R | `page,size` → página de animais do piquete |
| POST | `/api/v1/herd/animals/{id}/movements` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/destino/expectedVersion/data → movimento |
| POST | `/api/v1/herd/movements/batch` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/destino/animais → resultado em lote |
| GET | `/api/v1/herd/paddocks/{id}/movements` | CTX, R | `from,to,page,size` → página de movimentos |
| POST | `/api/v1/herd/animals/{id}/transfers` | CTX, OWNER/ADMIN/MANAGER | operationId/destino/expectedVersion → transferência |
| POST | `/api/v1/herd/transfers/batch` | CTX, OWNER/ADMIN/MANAGER | operationId/destino/animais → resultado em lote |
| GET | `/api/v1/herd/transfers` | CTX, R | filtros e `page,size` → página de transferências |
| GET | `/api/v1/herd/transfers/{id}` | CTX, R | path UUID → transferência |

## Inteligência e reprodução

| Método | Caminho | Contexto/papel | Request → response |
| --- | --- | --- | --- |
| GET | `/api/v1/herd/animals/{id}/weights` | CTX, R | `page,size` → página de pesagens |
| POST | `/api/v1/herd/animals/{id}/weights` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/peso/data/expectedVersion → resultado |
| POST | `/api/v1/herd/weights/batch` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/animais → resultado em lote |
| GET | `/api/v1/herd/animals/{id}/health-treatments` | CTX, R | `page,size` → página de tratamentos |
| POST | `/api/v1/herd/animals/{id}/health-treatments` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/tratamento/data → resultado |
| POST | `/api/v1/herd/health-treatments/batch` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/tratamento/animais → resultado em lote |
| POST | `/api/v1/herd/animals/{motherId}/breedings` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | serviço reprodutivo → gestação |
| POST | `/api/v1/herd/pregnancies/{id}/confirmation` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/expectedVersion/data → gestação |
| POST | `/api/v1/herd/pregnancies/{id}/termination` | CTX, OWNER/ADMIN/MANAGER | operationId/expectedVersion/motivo → gestação |
| POST | `/api/v1/herd/animals/{motherId}/calvings` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | parto e bezerro → resultado |
| GET | `/api/v1/herd/pregnancies/{id}` | CTX, R | path UUID → gestação |
| GET | `/api/v1/herd/animals/{motherId}/pregnancies` | CTX, R | `page,size` → página de gestações |
| GET | `/api/v1/herd/animals/{motherId}/calves` | CTX, R | `page,size` → página de filhos |
| GET | `/api/v1/herd/animals/{calfId}/mother` | CTX, R | path UUID → relação materna |

## Planejamento, pendências, agenda, relatórios e dashboard

| Método | Caminho | Contexto/papel | Request → response |
| --- | --- | --- | --- |
| POST | `/api/v1/herd/planner-items` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/tipo/título/data → item |
| PATCH | `/api/v1/herd/planner-items/{id}` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | campos/operationId/expectedVersion → item |
| POST | `/api/v1/herd/planner-items/{id}/completion` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/expectedVersion → item concluído |
| POST | `/api/v1/herd/planner-items/{id}/cancellation` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/expectedVersion → item cancelado |
| GET | `/api/v1/herd/planner-items/{id}` | CTX, R | path UUID → item |
| GET | `/api/v1/herd/planner-items` | CTX, R | filtros e `page,size` → página de itens |
| GET | `/api/v1/herd/pending-work` | CTX, R | `type,animalId,page,size` → página de pendências derivadas |
| GET | `/api/v1/herd/agenda` | CTX, R | período/filtros/`page,size` → agenda consolidada |
| GET | `/api/v1/herd/reports/herd-position` | CTX, R | filtros/`page,size` → posição do rebanho |
| GET | `/api/v1/herd/reports/lifecycle` | CTX, R | período/evento/animal/`page,size` → ciclo de vida |
| GET | `/api/v1/herd/reports/movements` | CTX, R | período/animal/piquetes/`page,size` → movimentos |
| GET | `/api/v1/herd/reports/transfers` | CTX, R | período/direção/animal/`page,size` → transferências |
| GET | `/api/v1/herd/reports/weights` | CTX, R | período/animal/categoria/`page,size` → pesagens |
| GET | `/api/v1/herd/reports/health` | CTX, R | período/tratamento/animal/`page,size` → saúde |
| GET | `/api/v1/herd/reports/reproduction` | CTX, R | período/matriz/status/`page,size` → reprodução |
| GET | `/api/v1/herd/reports/planner` | CTX, R | período/tipo/status/`page,size` → planejamento |
| GET | `/api/v1/herd/dashboard/overview` | CTX, R | período/categoria/sexo/piquete → visão geral |
| GET | `/api/v1/herd/dashboard/activity` | CTX, R | período → atividade |
| GET | `/api/v1/herd/dashboard/attention` | CTX, R | `previewSize` → itens de atenção |

## Inventário e insumos

| Método | Caminho | Contexto/papel | Request → response |
| --- | --- | --- | --- |
| POST | `/api/v1/inventory/products` | CTX, OWNER/ADMIN/MANAGER | id/nome/código/categoria/unidade → produto |
| GET | `/api/v1/inventory/products` | CTX, R | sem body → lista de produtos |
| GET | `/api/v1/inventory/products/{id}` | CTX, R | path UUID → produto |
| PATCH | `/api/v1/inventory/products/{id}` | CTX, OWNER/ADMIN/MANAGER | campos/expectedVersion → produto |
| POST | `/api/v1/inventory/locations` | CTX, OWNER/ADMIN/MANAGER | id/nome/código → local |
| GET | `/api/v1/inventory/locations` | CTX, R | sem body → lista de locais da fazenda |
| GET | `/api/v1/inventory/locations/{id}` | CTX, R | path UUID → local |
| PATCH | `/api/v1/inventory/locations/{id}` | CTX, OWNER/ADMIN/MANAGER | campos/expectedVersion → local |
| POST | `/api/v1/inventory/movements` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/tipo/produto/local/quantidade/data → movimento |
| GET | `/api/v1/inventory/stock` | CTX, R | filtros e `page,size` → página de saldos |
| GET | `/api/v1/inventory/locations/{id}/stock` | CTX, R | `page,size` → página de saldos do local |
| GET | `/api/v1/inventory/movements` | CTX, R | filtros e `page,size` → página do razão |
| GET | `/api/v1/inventory/movements/{id}` | CTX, R | path UUID → movimento |

## Finanças

| Método | Caminho | Contexto/papel | Request → response |
| --- | --- | --- | --- |
| POST | `/api/v1/finance/categories` | CTX, OWNER/ADMIN/MANAGER | nome/código/tipo → categoria |
| GET | `/api/v1/finance/categories` | CTX, R | sem body → lista de categorias |
| GET | `/api/v1/finance/categories/{id}` | CTX, R | path UUID → categoria |
| PATCH | `/api/v1/finance/categories/{id}` | CTX, OWNER/ADMIN/MANAGER | campos/expectedVersion → categoria |
| POST | `/api/v1/finance/entries` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/tipo/categoria/valor/vencimento → lançamento |
| GET | `/api/v1/finance/entries` | CTX, R | `dueFrom,dueTo` obrigatórios; demais filtros e `page,size` → página de lançamentos |
| GET | `/api/v1/finance/entries/{id}` | CTX, R | path UUID → lançamento |
| PATCH | `/api/v1/finance/entries/{id}` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | campos/expectedVersion → lançamento |
| POST | `/api/v1/finance/entries/{id}/settlement` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/expectedVersion/data → lançamento liquidado |
| POST | `/api/v1/finance/entries/{id}/cancellation` | CTX, OWNER/ADMIN/MANAGER/OPERATOR | operationId/expectedVersion/motivo → lançamento cancelado |
| GET | `/api/v1/finance/entries/{id}/history` | CTX, R | path UUID → eventos do lançamento |
| GET | `/api/v1/finance/summary` | CTX, R | `from,to,categoryId` → resumo financeiro |
| GET | `/api/v1/finance/summary/by-category` | CTX, R | `from,to` → totais por categoria |

## Convenções auditadas

- A superfície pública está integralmente sob `/api/v1`; Actuator e OpenAPI são superfícies operacionais separadas.
- Paginação é zero-based, ordenada de forma estável e limitada; valores máximos são 100 nos endpoints gerais e os padrões constam no OpenAPI.
- Comandos sensíveis rejeitam campos desconhecidos, duplicados e server-owned; IDs de tenant/fazenda nunca vêm do body.
- `operationId` cobre movimentos, transferências, ciclo de vida, inteligência, reprodução, inventário, finanças e planner onde replay é necessário.
- `expectedVersion` cobre correções e transições concorrentes. Criações administrativas simples preservam suas regras existentes para evitar mudança cosmética incompatível.
- Listas sem paginação são referências naturalmente pequenas no MVP (opções de contexto, produtos, locais e categorias). O crescimento real deve ser medido pelos clientes antes de alterar o contrato.
