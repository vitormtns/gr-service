# ADR 0002 — Monólito modular

## Estado da decisão

Aceita.

## Contexto

O domínio terá capacidades diferentes, mas o produto ainda não possui volume, equipe ou fronteiras maduras que justifiquem serviços independentes.

## Decisão

Construir uma aplicação Spring Boot única, organizada em módulos verticais. Cada módulo terá domínio, aplicação, infraestrutura e API próprios e não acessará tabelas internas de outro módulo.

## Consequências positivas

- Deploy, transações e diagnóstico permanecem simples.
- Limites explícitos facilitam testes e uma extração futura seletiva.

## Consequências negativas

- Disciplina e ArchUnit são necessários para evitar acoplamento interno.
- Escala de deploy é conjunta enquanto o módulo não for extraído.

## Alternativas consideradas

- Microserviços desde o início: rejeitados por custo distribuído sem requisito comprovado.
- Pacotes globais por camada: rejeitados porque escondem fronteiras de negócio.
- Monólito sem módulos: rejeitado pelo risco de dependências irrestritas.
