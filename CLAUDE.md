# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

All commands run from `pfc/` (the Maven module root).

| Action | Command |
|--------|---------|
| Run app | `./mvnw spring-boot:run` |
| Run all tests | `./mvnw test` |
| Run single test class | `./mvnw test -Dtest=AccountServiceTest` |
| Build (skip tests) | `./mvnw clean package -DskipTests` |

The `spring-boot-maven-plugin` is configured in `pom.xml` to activate the `local` Spring profile automatically, so no `-D` flags are needed. Local DB credentials live in `src/main/resources/application-local.yaml` (gitignored).

Start the database before running: `docker compose up -d` (from `pfc/`).

## Architecture

**Package-by-feature** under `com.pfc`. Each feature owns its full vertical slice:

```
com.pfc.
├── account/        Controller · Service · Repository · Account · AccountType · dto/
├── category/       Controller · Service · Repository · Category · CategoryType · dto/
├── transaction/    Controller · Service · Repository · Transaction · TransactionType · dto/
├── budget/         Controller · Service · Repository · Budget · dto/
├── report/         Controller · Service · dto/   ← no entity; aggregation only
└── shared/
    ├── config/     OpenApiConfig
    └── exception/  GlobalExceptionHandler · ResourceNotFoundException · BusinessException
```

Feature dependency order (also the safe build order):
`shared` → `account` & `category` (independent) → `transaction` → `budget` → `report`

## Key patterns

**Service layer**: class-level `@Transactional(readOnly = true)`; write methods override with `@Transactional`. Repositories are injected via constructor.

**DTO conversion**: entities never leave the service layer. Each service has a private `toResponse(Entity e)` helper. Response DTOs use `@Getter @AllArgsConstructor`; request DTOs use `@Getter @Setter` with field-level validation annotations.

**Lombok**: entities and DTOs use Lombok (`@Getter`/`@Setter`/`@AllArgsConstructor`) instead of hand-written accessors. Avoid `@Data` on JPA entities — it generates `equals`/`hashCode`/`toString` over all fields, which causes problems with lazy-loaded associations and Hibernate proxies; prefer `@Getter @Setter` there.

**Error handling**: throw `ResourceNotFoundException("Account", id)` for 404 or `BusinessException("message")` for 422. `GlobalExceptionHandler` in `shared/exception/` converts both to `ProblemDetail` (RFC 7807). Never catch these inside controllers.

**Report feature**: `ReportService` performs all aggregation in-memory using streams over repository results. It has no JPA entity and creates no new migrations. Query parameters use `YYYY-MM` format for months.

**Database schema**: a single Flyway migration `V1__init.sql` defines all four tables. Schema changes require a new versioned migration file — never edit an existing one or alter the schema manually.

## Domain invariants

- Money: always `BigDecimal` / `NUMERIC(15,2)` — never `double` or `float`
- PKs: `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)` (Hibernate 6+)
- Enums stored as `VARCHAR` via `@Enumerated(EnumType.STRING)`
- `Transaction.type` and `Category.type` share the same values: `INCOME` / `EXPENSE`
- `Budget` has a unique constraint on `(category_id, reference_month)`; attempting a duplicate throws `BusinessException`

## Adding a new feature

1. Create a package under `com.pfc.<feature>/`
2. Add the JPA entity (`@GeneratedValue(strategy = GenerationType.UUID)`, with `@Getter @Setter` from Lombok)
3. Add a Flyway migration `VN__<description>.sql` if new tables are needed
4. Follow the Controller → Service → Repository → Entity/DTO stack
5. Add a unit test for the service (`src/test/java/com/pfc/<feature>/`)
6. Run `./mvnw test` before finishing
7. Update documentation (see below)

## Documentation — update without being asked

Every task that adds, changes or removes behavior **must** update the relevant docs before closing. Do not wait for the user to request it.

| What changed | What to update |
|---|---|
| New feature or endpoint | `CHANGELOG.md` (under `[Não lançado]`) + Javadoc on public service methods |
| Architectural decision with a trade-off | New `docs/adr/ADR-NNN-<slug>.md` — use the format of existing ADRs |
| Breaking change or incompatible decision | New ADR with `Status: Accepted`, mark superseded ADR with `Status: Superseded by ADR-NNN` |
| New dependency added | Note it in `CHANGELOG.md` and justify in an ADR if it carries a non-trivial trade-off |
| Schema change | New Flyway migration + update `CHANGELOG.md` |

**CHANGELOG format:** add one bullet per user-visible change under `## [Não lançado] > ### Adicionado / Alterado / Corrigido / Removido`. Follow the existing entries as a style guide.

**Javadoc rule:** public methods on Service classes and custom queries on Repository interfaces. Skip getters, setters, trivial CRUD — document the *why* or non-obvious behavior only. Write in Portuguese.
