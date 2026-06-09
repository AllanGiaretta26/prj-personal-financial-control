# ADR-007: Usuário de banco de dados de privilégio mínimo

**Status:** Accepted
**Data:** 2026-06-08

## Contexto

Até aqui, a aplicação e o Flyway conectavam ao Postgres com a mesma role (`pfc`), que é dona do schema `public` e, portanto, tem privilégios completos de DDL (criar, alterar e apagar tabelas, e potencialmente o próprio banco).

Isso viola o princípio de **menor privilégio** já adotado como meta em `SECURITY.md`: em runtime, a aplicação só precisa ler e escrever linhas nas quatro tabelas da feature (`account`, `category`, `transaction`, `budget`) — nunca alterar a estrutura delas. Se a API for comprometida (bug, dependência vulnerável, injeção de SQL via *native query* mal escrita), uma role com privilégios de DDL permite que o atacante apague tabelas, altere colunas ou corrompa o schema — um raio de explosão (*blast radius*) muito maior do que o necessário.

O Flyway, por outro lado, **precisa** de privilégios de DDL para aplicar migrations (`CREATE TABLE`, `ALTER TABLE` etc.), o que cria um conflito direto: a role usada para migrar o schema não pode ser a mesma usada pela aplicação em runtime, sob pena de a aplicação herdar privilégios que não deveria ter.

## Decisão

Separar o acesso ao banco em **duas roles do Postgres**, cada uma usada por um componente distinto:

- **`pfc`** — dona do schema `public` e das tabelas. Usada **apenas pelo Flyway**, em uma conexão dedicada (`spring.flyway.url/user/password`), exclusivamente para aplicar migrations. Mantém privilégios de DDL.
- **`pfc_app`** — role de runtime usada pela aplicação (`spring.datasource.*`). Recebe apenas `SELECT, INSERT, UPDATE, DELETE` nas tabelas — **nenhum** privilégio de DDL, e não é dona de nada.

Dois mecanismos tornam isso possível sem código customizado:

1. **`spring.flyway.url` / `spring.flyway.user` / `spring.flyway.password`** — propriedades nativas do Spring Boot que sobrepõem `spring.datasource.*` *apenas* para a conexão usada pelo Flyway ao migrar. O restante da aplicação continua usando `spring.datasource.*` normalmente. Em `application.yaml`:

   ```yaml
   spring:
     datasource:
       url: ${DB_URL}
       username: ${DB_USERNAME}
       password: ${DB_PASSWORD}
     flyway:
       url: ${FLYWAY_URL:${DB_URL}}
       user: ${FLYWAY_USERNAME}
       password: ${FLYWAY_PASSWORD}
   ```

2. **`ALTER DEFAULT PRIVILEGES FOR ROLE pfc IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO pfc_app`** — resolve o problema de ordem "ovo e galinha": no primeiro `docker compose up`, a role `pfc_app` é criada (script de inicialização do Postgres) *antes* de o Flyway rodar e criar as tabelas. Sem esse comando, `pfc_app` não teria nenhum acesso às tabelas recém-criadas, pois grants normais (`GRANT ... ON TABLES`) só valem para tabelas que já existem no momento em que são executados. O `ALTER DEFAULT PRIVILEGES` instrui o Postgres a aplicar automaticamente os grants informados a **qualquer tabela futura** criada pela role `pfc` no schema `public` — independente da ordem de execução entre o script de setup e as migrations.

Para o ambiente local, foi criado `pfc/db-init/01-create-app-role.sql`, montado em `/docker-entrypoint-initdb.d/` no `docker-compose.yml`. Esse script cria a role `pfc_app`, concede `USAGE` no schema e configura o `ALTER DEFAULT PRIVILEGES` acima. Como scripts em `docker-entrypoint-initdb.d` não passam pela substituição de variáveis do `docker-compose` (não recebem `envsubst`), a senha de `pfc_app` é fixa (`pfc_app`) no script — mesmo padrão de conveniência já usado para a role `pfc` (usuário/senha `pfc`/`pfc` por padrão no `docker-compose.yml`). Em produção (Render), as credenciais de cada role são geradas e injetadas via variáveis de ambiente próprias (`DB_USERNAME`/`DB_PASSWORD` para `pfc_app`, `FLYWAY_USERNAME`/`FLYWAY_PASSWORD` para `pfc`), nunca usando os valores do script local — ver `SECURITY.md`.

## Consequências

**Positivas:**
- **Defesa em profundidade e redução de raio de explosão**: mesmo que a aplicação seja comprometida, a role `pfc_app` não consegue executar `CREATE TABLE`, `ALTER TABLE`, `DROP TABLE` nem `DROP DATABASE` — o atacante fica limitado a manipular linhas dentro das tabelas existentes (já mitigado por outras camadas, como Bean Validation e parametrização de queries).
- **Nenhum código customizado**: a separação usa exclusivamente propriedades nativas do Spring Boot (`spring.flyway.url/user/password`) e um mecanismo padrão do Postgres (`ALTER DEFAULT PRIVILEGES`), sem necessidade de `DataSource` ou `FlywayMigrationStrategy` escritos à mão.
- **Consistente entre ambientes**: o mesmo padrão de duas roles (uma dona do schema, outra de runtime restrito) se aplica igualmente em desenvolvimento local e produção — só muda *como* as credenciais chegam até a aplicação (script de init local vs. variáveis de ambiente do Render).

**Negativas / trade-offs:**
- **Setup local mais complexo**: agora existem duas roles e duas senhas para gerenciar localmente, além de um script de inicialização adicional (`db-init/01-create-app-role.sql`) que só roda na *primeira* subida do volume do Postgres — recriar o ambiente do zero exige `docker compose down -v` para forçar sua reexecução.
- **Duas credenciais em produção**: o painel de variáveis de ambiente do Render passa a precisar de quatro segredos relacionados ao banco (`DB_USERNAME`/`DB_PASSWORD` e `FLYWAY_USERNAME`/`FLYWAY_PASSWORD`) em vez de dois, aumentando ligeiramente a superfície de gestão de segredos (mas não o risco — pelo contrário, é exatamente essa separação que reduz o risco geral).
- **Senha de desenvolvimento fixa no script**: como `docker-entrypoint-initdb.d` não recebe substituição de variáveis do `docker-compose`, a senha de `pfc_app` fica hardcoded em `db-init/01-create-app-role.sql`. Isso é aceitável porque (a) é um valor *apenas* de conveniência local, documentado como tal no próprio script, e (b) segue o mesmo padrão já existente para a role `pfc` no `docker-compose.yml`.
