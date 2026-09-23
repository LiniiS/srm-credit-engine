# ADR-0002: Adotar PostgreSQL, Flyway, JPA e jOOQ

- **Status:** Proposto
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** persistência ACID, extrato analítico, Docker Compose

## Contexto

A escrita exige integridade relacional e a leitura exige SQL controlável em volume. O schema precisa ser reproduzível localmente e nos testes.

## Opções consideradas

1. **PostgreSQL 16 + Flyway + JPA na escrita + jOOQ na leitura** — adequa modelo e ferramenta ao uso; adiciona dois estilos de acesso.
2. **JPA para tudo** — menor variedade; consultas/planos analíticos menos explícitos.
3. **jOOQ para tudo** — SQL explícito; maior verbosidade para agregados transacionais simples.
4. **Supabase** — conveniência gerenciada; viola restrição e reduz reprodutibilidade local.

## Decisão

Propor opção 1 no mesmo banco, um CQRS leve sem event sourcing. Flyway é a única fonte do schema; PostgreSQL Testcontainers valida migrações e concorrência.

## Limites e regras resultantes

- Reporting pode ler tabelas transacionais via jOOQ, mas não chama services de settlement.
- Nenhum H2 é permitido para persistência/concorrência.
- DDL documental é derivado das migrations, não mantido como fonte paralela.

## Verificação

- Testcontainers PostgreSQL 16 executa todas as migrations.
- Testes jOOQ e `EXPLAIN ANALYZE` verificam filtros/índices do extrato.

## Consequências

- Positivas: ACID, SQL observável e ambiente fiel.
- Negativas: geração/configuração jOOQ aumenta o build.
- Revisitar se: volume analítico justificar store separado, fora deste MVP.

