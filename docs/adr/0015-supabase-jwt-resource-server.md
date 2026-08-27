# ADR 0015 — JWT do Supabase validado pelo Resource Server

## Estado da decisão

Aceita.

## Contexto

A API precisa autenticar access tokens do Supabase Auth sem delegar autorização de negócio ao provedor. Ambientes podem usar signing keys assimétricas publicadas por JWKS ou uma chave simétrica legada, e aceitar ambos silenciosamente aumentaria a superfície de confiança.

## Decisão

O Spring Security OAuth2 Resource Server será a autoridade para validar bearer tokens. O modo será escolhido explicitamente entre `JWKS` e `HMAC`, com um único algoritmo permitido, issuer, audience, janela temporal e claims obrigatórios validados. JWKS será preferido para assinatura assimétrica; HMAC aceitará somente `HS256`, exigirá segredo de backend e não será fallback automático.

O UUID validado de `sub` formará a identidade canônica da requisição. A role `authenticated` produzirá apenas a autoridade técnica `ROLE_AUTHENTICATED`. E-mail, `aal` e sessão serão atributos opcionais; nenhum claim definirá organização, fazenda ou papel de membership.

## Consequências positivas

- Assinatura e claims são verificados antes de alcançar controllers.
- Rotação de chaves assimétricas pode usar o endpoint JWKS sem distribuir segredo à API.
- A identidade consumida pela aplicação não depende de `Jwt` nem carrega o token bruto.

## Consequências negativas

- Configuração ausente ou contraditória impede a inicialização deliberadamente.
- Projetos HMAC legados exigem gestão segura de um segredo no backend.
- Autenticação não resolve usuário interno, tenant, fazenda ou autorização organizacional.

## Alternativas consideradas

- Interpretar JWT no controller: rejeitado por duplicar segurança e misturar HTTP com criptografia.
- Consultar o Auth server em toda requisição: rejeitado como padrão por latência e disponibilidade; poderá ser reavaliado para HMAC remoto legado.
- Detectar JWKS ou HMAC pelo token: rejeitado por permitir fallback silencioso e ampliar algoritmos confiáveis.
