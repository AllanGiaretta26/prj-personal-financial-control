-- ===========================================================================
-- Render — criação da role de runtime de privilégio mínimo (pfc_app)
-- ===========================================================================
--
-- ESTE É O MODELO VERSIONADO. Copie-o para `create-app-role.sql` (que é
-- gitignorado), troque a senha por uma forte e rode esse arquivo. Assim a
-- senha real nunca entra no histórico do git:
--     cp create-app-role.example.sql create-app-role.sql
--     # edite a senha em create-app-role.sql
--     psql "<EXTERNAL_DATABASE_URL_DO_RENDER>" -f create-app-role.sql
--
-- O Postgres gerenciado do Render entrega UMA única role (a dona do banco,
-- com nome gerado, ex.: "pfc_db_xxxx"). Este projeto, porém, usa DUAS roles
-- (ADR-007):
--   - dona do schema  -> roda o Flyway/DDL  -> use a role do Render como
--                        FLYWAY_USERNAME / FLYWAY_PASSWORD
--   - pfc_app (runtime) -> só DML, sem DDL  -> use como DB_USERNAME / DB_PASSWORD
--
-- Rode ANTES do primeiro deploy (recomendado): como o Flyway ainda não criou
-- as tabelas, o ALTER DEFAULT PRIVILEGES abaixo garante que pfc_app receba DML
-- em todas as tabelas que a role dona criar nas migrations.
--
-- IMPORTANTE: a senha definida aqui é a mesma usada em DB_PASSWORD nas
-- variáveis de ambiente do web service.
-- ===========================================================================

-- 1) Cria a role de runtime. TROQUE A SENHA antes de rodar.
CREATE ROLE pfc_app WITH LOGIN PASSWORD 'TROCAR-POR-SENHA-FORTE';

-- 2) Deixa pfc_app enxergar/usar o schema public.
GRANT USAGE ON SCHEMA public TO pfc_app;

-- 3) DML nas tabelas que já existem (caso este script rode após o Flyway).
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO pfc_app;

-- 4) DML automático nas tabelas FUTURAS criadas pela role dona (CURRENT_USER)
--    — é a role que roda este script e também roda o Flyway. Sem FOR ROLE, o
--    default privilege se aplica aos objetos criados por quem executa o ALTER.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO pfc_app;
