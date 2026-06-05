# Changelog

Todas as mudanças relevantes deste projeto são documentadas neste arquivo.

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e o projeto adota o [Versionamento Semântico](https://semver.org/lang/pt-BR/)
(`MAJOR.MINOR.PATCH`).

<!--
Convenção de preenchimento (ler antes de editar):
- Mantenha a seção [Não lançado] no topo. Ao lançar, mova o conteúdo dela para
  uma nova seção de versão datada, ex.: ## [0.1.0] - 2026-06-10.
- Ordem cronológica reversa: versão mais recente sempre no topo.
- Agrupe cada mudança em uma destas categorias (omita as vazias):
  Adicionado · Alterado · Obsoleto · Removido · Corrigido · Segurança.
- Uma linha por mudança, escrita para quem lê o changelog — não cópia da
  mensagem de commit.
- Numeração: PATCH para correção, MINOR para feature compatível, MAJOR para
  mudança que quebra compatibilidade.
-->

## [Não lançado]

### Adicionado
- Documento de arquitetura (`ARCHITECTURE.md`) com o design das features, modelo de dados e ADRs.
- Política e práticas de segurança (`SECURITY.md`).
- `AGENTS.md` com instruções para agentes de IA que trabalham no repositório.
- `CLAUDE.md` com orientações específicas para Claude Code.
- Este changelog.

## [0.1.0] - 2026-06-05

### Adicionado
- Estrutura inicial do projeto Spring Boot 3.5 com Java 21 e Maven.
- `docker-compose.yml` com PostgreSQL 16 para ambiente local.
- Perfil `local` em `application-local.yaml`; plugin Maven configurado para ativá-lo automaticamente via `./mvnw spring-boot:run`.
- Migration Flyway `V1__init.sql` com o schema completo: tabelas `account`, `category`, `transaction` e `budget`.
- Pacote `shared`: `GlobalExceptionHandler` com respostas `ProblemDetail` (RFC 7807), `ResourceNotFoundException` (404) e `BusinessException` (422), e `OpenApiConfig` com Swagger UI em `/swagger-ui.html`.
- Feature **account**: CRUD completo (`GET /api/v1/accounts`, `POST`, `PUT /{id}`, `DELETE /{id}`); enum `AccountType` (`CHECKING`, `SAVINGS`, `WALLET`, `CASH`).
- Feature **category**: CRUD completo (`GET /api/v1/categories`, `POST`, `PUT /{id}`, `DELETE /{id}`); enum `CategoryType` (`INCOME`, `EXPENSE`).
- Feature **transaction**: CRUD completo (`GET /api/v1/transactions`, `POST`, `PUT /{id}`, `DELETE /{id}`); valida existência de conta e categoria referenciadas.
- Feature **budget**: CRUD completo (`GET /api/v1/budgets`, `POST`, `PUT /{id}`, `DELETE /{id}`); rejeita duplicata de `(category, referenceMonth)` com 422.
- Feature **report** (somente leitura, sem entidade JPA):
  - `GET /api/v1/reports/spending?month=YYYY-MM` — gastos por categoria no mês.
  - `GET /api/v1/reports/budget-comparison?month=YYYY-MM` — orçado vs. realizado.
  - `GET /api/v1/reports/account-balances` — saldo atual por conta.
- Testes unitários para `AccountService`, `CategoryService`, `TransactionService` e `BudgetService` com JUnit 5 + Mockito (sem Spring context).
