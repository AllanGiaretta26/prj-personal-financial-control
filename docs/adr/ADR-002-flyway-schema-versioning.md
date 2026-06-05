# ADR-002: Flyway para versionamento de schema desde o início

**Status:** Accepted  
**Data:** 2026-06-05

## Contexto

Durante o desenvolvimento inicial de um projeto Spring Boot é comum usar a propriedade `spring.jpa.hibernate.ddl-auto: create` ou `update` para deixar o Hibernate gerenciar o schema automaticamente. Essa abordagem é rápida para protótipos, mas perde rastreabilidade e previsibilidade à medida que o projeto evolui.

## Decisão

Adotar o Flyway para gerenciamento de schema desde o primeiro commit, desabilitando o `ddl-auto` do Hibernate (`validate`). Toda alteração de schema é feita por meio de migrations versionadas em `src/main/resources/db/migration`.

## Consequências

**Positivas:**
- O schema está versionado junto ao código-fonte, permitindo saber exatamente qual estado do banco corresponde a qual commit.
- Deploys são reproduzíveis: qualquer ambiente (local, staging, produção) parte do mesmo baseline e aplica as mesmas migrations na mesma ordem.
- O histórico de evolução do schema é rastreável e auditável.

**Negativas / trade-offs:**
- Exige a criação de um arquivo de migration a cada mudança de schema, mesmo em fases iniciais de experimentação onde o modelo de dados ainda está sendo descoberto.
- Renomear uma coluna requer uma migration de `ALTER TABLE` em vez de apenas editar a entidade JPA.
