-- Cria a role de runtime de privilégio mínimo usada pela aplicação (pfc_app).
--
-- Este script roda automaticamente na primeira inicialização do container
-- Postgres (via /docker-entrypoint-initdb.d/), com a role definida em
-- POSTGRES_USER (padrão "pfc") já criada como dono do banco/schema — ela é
-- quem roda as migrations do Flyway (DDL: criar/alterar tabelas).
--
-- A role "pfc_app" é a usada pela aplicação em runtime: só pode
-- SELECT/INSERT/UPDATE/DELETE nas tabelas, sem nenhum privilégio de DDL
-- (não pode criar, alterar ou apagar tabelas, nem o banco). Esse é o
-- "usuário de banco de privilégio mínimo" descrito no SECURITY.md.
--
-- NOTA — credenciais: scripts em /docker-entrypoint-initdb.d/ NÃO recebem
-- substituição de variáveis do docker-compose (não passam por envsubst), por
-- isso a senha abaixo é fixa ("pfc_app"), seguindo o mesmo padrão de
-- conveniência local já usado para a role "pfc" (usuário/senha "pfc"/"pfc"
-- no docker-compose.yml). Em produção (Render), as credenciais de cada role
-- são geradas/injetadas via variáveis de ambiente (FLYWAY_USERNAME/
-- FLYWAY_PASSWORD para a role dona do schema, DB_USERNAME/DB_PASSWORD para a
-- role de runtime) e nunca usam estes valores — ver SECURITY.md.
CREATE ROLE pfc_app WITH LOGIN PASSWORD 'pfc_app';

-- Permite que pfc_app "enxergue" e use objetos do schema public (sem isso,
-- nem SELECT funciona).
GRANT USAGE ON SCHEMA public TO pfc_app;

-- Concede DML (sem DDL) nas tabelas que já existem no momento em que este
-- script roda. Na prática, como o Flyway ainda não rodou na primeira
-- inicialização, isso normalmente não encontra tabelas — mas é mantido por
-- segurança/clareza caso a ordem mude.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO pfc_app;

-- Resolve o problema de ordem "ovo e galinha": como o Flyway roda DEPOIS
-- deste script (com a role "pfc", dona do schema) e cria as tabelas da
-- aplicação, precisamos garantir que pfc_app receba automaticamente
-- SELECT/INSERT/UPDATE/DELETE em qualquer tabela que "pfc" criar no futuro —
-- sem isso, pfc_app ficaria sem acesso às tabelas recém-migradas.
ALTER DEFAULT PRIVILEGES FOR ROLE pfc IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO pfc_app;
