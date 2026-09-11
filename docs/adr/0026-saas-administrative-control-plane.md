# ADR 0026 — SaaS Administrative Control Plane

## Decisão

Organizations, farms, memberships e farm scopes existentes continuam sendo o modelo canônico. Organizations e farms não são apagadas fisicamente; a atualização administrativa usa `expectedVersion` e no-op não altera versão. A criação recebe UUID do cliente, é idempotente pelo identificador e cria, na mesma transação, a membership `OWNER` com `ALL_FARMS`.

`OWNER` administra todas as memberships; `ADMIN` somente `MANAGER`, `OPERATOR` e `VIEWER`; os demais não administram. A última membership `OWNER` ativa não pode ser rebaixada ou revogada. `SELECTED_FARMS` exige fazendas ativas da mesma organization; `ALL_FARMS` não persiste scopes e inclui novas fazendas automaticamente.

Invitations guardam apenas SHA-256 de um token aleatório de 256 bits. O token é devolvido uma única vez na criação para entrega manual no MVP, nunca em consultas ou logs. A aceitação exige JWT autenticado e e-mail interno sincronizado igual ao destinatário normalizado; membership, scopes e aceitação são uma transação. Expiração é derivada de `expires_at` (sete dias); não há scheduler ou provedor de e-mail.

Eventos append-only em `platform_admin_events` fornecem auditoria administrativa. As tabelas novas usam RLS/`FORCE RLS`; o repositório configura explicitamente user e organization no contexto PostgreSQL, sem bypass global. A criação e aceitação, que ainda não possuem contexto de tenant, usam funções `SECURITY DEFINER` estreitas e com privilégios revogados do público.

O Supabase Auth permanece a fronteira de identidade. Billing, SSO/SCIM, grupos, permissões customizadas, e-mail externo, MFA, exclusão física, merge e transferência de organizations estão fora de escopo. Hardening posterior tratará corridas de last-owner e accept, fault injection e auditoria de segurança final.
