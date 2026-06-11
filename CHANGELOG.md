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
- Suporte a deploy no **Render** via Docker: `pfc/Dockerfile` (build multi-stage com imagem Maven/Temurin 21 e runtime JRE 21 como usuário sem privilégios) e `pfc/.dockerignore`. A aplicação é configurada por variáveis de ambiente já existentes (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `FLYWAY_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`), sem ativar o perfil `local`.
- Script `pfc/deploy/render/create-app-role.sql` para criar a role de runtime de privilégio mínimo (`pfc_app`) no Postgres gerenciado do Render, preservando a separação DDL/runtime do ADR-007.
- Endpoint público de **health check** `GET /actuator/health` (dependência `spring-boot-starter-actuator`), para a verificação de saúde da plataforma de deploy. Apenas o `health` é exposto e sem detalhes de componentes (`show-details`/`show-components: never`), para não vazar configuração interna.

## [0.2.0] - 2026-06-08

### Adicionado
- Documento de arquitetura (`ARCHITECTURE.md`) com o design das features, modelo de dados e ADRs.
- Política e práticas de segurança (`SECURITY.md`).
- `AGENTS.md` com instruções para agentes de IA que trabalham no repositório.
- `CLAUDE.md` com orientações específicas para Claude Code.
- Este changelog.
- Dependência `lombok` (escopo `provided`/opcional) para reduzir código boilerplate em entidades e DTOs.
- Autenticação via **Spring Security + JWT** (pacote `com.pfc.auth`): `POST /api/v1/auth/register` e `POST /api/v1/auth/login` emitem um token (HMAC-SHA256, biblioteca `jjwt`); endpoints protegidos passam a exigir `Authorization: Bearer <token>`. Senhas armazenadas com hash `BCrypt`. Migration `V2__create_users.sql` cria a tabela `users`. Veja `docs/adr/ADR-008-autenticacao-jwt-e-modelo-de-ownership.md`.
- **Autorização por dono (ownership)**: cada conta, categoria, transação e orçamento passa a pertencer a um usuário (`user_id` explícito nas quatro tabelas, migration `V3__add_user_ownership.sql`); um usuário só enxerga e manipula os próprios dados — inclusive nos relatórios agregados (`ReportService`). Tentativas de acessar ou referenciar recurso de outro usuário retornam `404 Not Found` (nunca `403`), para não vazar a existência do recurso (proteção contra IDOR). Veja `docs/adr/ADR-008-autenticacao-jwt-e-modelo-de-ownership.md`.
- Cabeçalhos HTTP de segurança habilitados via `SecurityConfig`: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` e `Strict-Transport-Security` (HSTS, `includeSubDomains`, 1 ano).
- Política de **CORS restrita por configuração** (`app.cors.allowed-origins`, variável `CORS_ALLOWED_ORIGINS`) — nunca `*`.
- **Limitação de taxa (rate limiting)** com Bucket4j (`com.bucket4j:bucket4j_jdk17-core`): perfil estrito por IP em `/api/v1/auth/login` e `/api/v1/auth/register` (padrão 5 req/min, anti-força-bruta) e perfil generoso por usuário autenticado nas demais rotas `/api/v1/**` (padrão 60 req/min); excesso responde `429 Too Many Requests` com corpo `ProblemDetail` e cabeçalho `Retry-After`. Limites configuráveis via `rate-limit.*` / variáveis `RATE_LIMIT_*`. Veja `docs/adr/ADR-009-rate-limiting-com-bucket4j.md`.
- **Usuário de banco de privilégio mínimo**: a aplicação passa a conectar como a role restrita `pfc_app` (apenas `SELECT/INSERT/UPDATE/DELETE`, sem DDL); o Flyway usa uma conexão dedicada (`spring.flyway.url/user/password`) com a role dona do schema. Script `pfc/db-init/01-create-app-role.sql` cria a role e os privilégios padrão (`ALTER DEFAULT PRIVILEGES`) no ambiente local. Veja `docs/adr/ADR-007-usuario-banco-privilegio-minimo.md`.
- **Testcontainers** nos testes de integração: `AbstractIntegrationTest` sobe um Postgres real (`@ServiceConnection`) durante `./mvnw test`, substituindo o uso de H2/mocks nessa camada; novos testes `*ControllerIT` (account, category, transaction, budget, auth) cobrem o fluxo HTTP completo, incluindo autenticação, isolamento entre usuários e rate limiting.

### Alterado
- Entidades JPA (`Account`, `Category`, `Transaction`, `Budget`) e DTOs de request/response refatorados para usar anotações Lombok (`@Getter`, `@Setter`, `@AllArgsConstructor`) no lugar de getters/setters/construtores escritos manualmente.
- `TransactionResponse` e `BudgetResponse` passaram a ser construídos via construtor all-args (alinhando com o padrão já adotado por `AccountResponse`/`CategoryResponse`), em vez de `new` + setters.
- `Account`, `Category`, `Transaction`, `Budget` e `ReportService` passam a resolver e filtrar todos os dados pelo usuário autenticado (`AuthenticatedUserProvider`), nunca pela base inteira.

### Corrigido
- Removido bean redundante `DaoAuthenticationProvider` de `SecurityConfig` — o `AuthenticationManager` já é montado automaticamente a partir de `CustomUserDetailsService` + `PasswordEncoder`, e a declaração manual apenas duplicava a configuração (gerando um aviso do Spring Security no startup).
- Erros de cliente em rotas protegidas (corpo JSON malformado, id com formato inválido, método/rota não suportados) retornavam `401 Unauthorized` — mascarados pelo encaminhamento ao endpoint interno `/error`, que é autenticado. Agora o `GlobalExceptionHandler` estende `ResponseEntityExceptionHandler` e devolve o status HTTP correto (`400`/`404`/`405`...) em `ProblemDetail`.

### Segurança
- `GlobalExceptionHandler` ganhou uma rede de segurança (`@ExceptionHandler(Exception.class)`) que responde `500 Internal Server Error` genérico para qualquer exceção inesperada — sem stacktrace nem detalhe interno no corpo; o erro real é registrado apenas no log do servidor.
- `server.error.include-stacktrace`, `include-message` e `include-binding-errors` fixados em `never` no `application.yaml` (defesa em profundidade, independente do default do framework).

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
