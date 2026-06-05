# AGENTS.md

> Instruções para agentes de IA (Claude Code, Codex, Cursor) que trabalham neste repositório.
> Para humanos: veja `README.md`, `ARCHITECTURE.md`, `CHANGELOG.md` e `SECURITY.md`.

## Projeto

**Personal Financial Control** — API REST de controle de finanças pessoais:
contas, categorias, transações, orçamentos e relatórios de agregação (gastos por
categoria, orçado vs. realizado, saldo por conta). Sem interface gráfica; a
entrega é a API documentada via OpenAPI.

**Stack:** Java 21, Spring Boot 3.x, Maven, PostgreSQL, Flyway, Bean Validation, springdoc-openapi.

> ⚠️ A API ainda **não tem autenticação**. Enquanto não tiver, só vai ao ar com
> **dados fictícios** — nunca dados financeiros reais. Ver `SECURITY.md`.

## Setup

```bash
# Pré-requisitos: JDK 21, Docker
docker compose up -d        # sobe o PostgreSQL
cp .env.example .env        # preencher DATABASE_URL, DB_USERNAME, DB_PASSWORD
./mvnw spring-boot:run      # API em http://localhost:8080
```

Doc interativa (Swagger UI): `http://localhost:8080/swagger-ui.html`

## Comandos

| Ação    | Comando                  |
| ------- | ------------------------ |
| Build   | `./mvnw clean install`   |
| Testes  | `./mvnw test`            |
| Rodar   | `./mvnw spring-boot:run` |
| Javadoc | `./mvnw javadoc:javadoc` |

Sempre rode `./mvnw test` antes de concluir uma tarefa e relate o resultado.

## Estrutura — organização POR FEATURE

`package-by-feature`, **não** por camada. Cada feature é um pacote autocontido
com suas próprias camadas. NÃO crie pastas globais `controllers/`, `services/`,
`repositories/`. (Detalhe completo em `ARCHITECTURE.md`.)

```
src/main/java/com/pfc/
├── account/        # Contas (Controller, Service, Repository, Account, dto/)
├── category/       # Categorias
├── transaction/    # Transações (receita/despesa)
├── budget/         # Orçamentos por categoria/mês
├── report/         # Relatórios (agregações, sem entidade)
└── shared/         # transversal: config/ e exception/
src/main/resources/
└── db/migration/   # scripts Flyway (V1__init.sql, ...)
```

**Dependências entre features** (define a ordem e o que é paralelizável):
`shared` → `account` e `category` (independentes) → `transaction` (usa as duas)
→ `budget` → `report` (lê tudo).

## Camadas dentro de cada feature

`HTTP → Controller → Service → Repository → Banco`

- **Controller:** recebe/responde HTTP, valida o DTO de entrada. Sem regra de negócio.
- **Service:** regra de negócio e `@Transactional`. Não conhece HTTP.
- **Repository:** acesso a dados (Spring Data JPA).
- **Entidade:** mapeia a tabela; nunca sai pela API direto.
- **DTO:** contrato da API (request/response); sem lógica.

## Regras de domínio (não-negociáveis)

- **Dinheiro em `BigDecimal` / `DECIMAL`, NUNCA `double`/`float`** — ponto
  flutuante acumula erro de arredondamento.
- **UUID como chave primária** em todas as entidades.
- **`type` como enum** (`INCOME`/`EXPENSE`).
- **Schema só muda via migration Flyway nova.** Nunca edite uma migration já
  aplicada nem altere o schema na mão.
- **Validação na borda** (Bean Validation no DTO/Controller), antes da regra de negócio.
- **Erros centralizados** num `@RestControllerAdvice` global, resposta em
  `ProblemDetail` (RFC 7807): `ResourceNotFoundException` (404),
  `BusinessException` (422/400). Não trate erro dentro do controller.

## Segurança (ver `SECURITY.md`)

- Segredos via variáveis de ambiente — nunca no código nem no Git. `application.yml`
  referencia `${DB_PASSWORD}`, nunca o valor.
- Não logar dados sensíveis (senhas, tokens, valores que identifiquem o usuário).
- Queries parametrizadas; proibido montar SQL nativo concatenando input do usuário.

## Documentação — onde está a verdade

NÃO duplique informação. Cada fonte tem um dono:

- **Design e decisões macro** → `ARCHITECTURE.md` (estrutura, modelo de dados, trade-offs).
- **Segurança** → `SECURITY.md` (em vigor vs. planejado, checklist de deploy).
- **Decisões novas/granulares** → `adr/`. Registre decisões com trade-off relevante;
  mudou de ideia, crie um novo ADR com `superseded` no antigo — nunca edite um aceito.
- **Contrato de classes/métodos públicos** → Javadoc no código (só API pública).
- **API HTTP** → anotações OpenAPI/Swagger nos controllers.

## Regras para o agente

- Explique o *porquê* de escolhas não-óbvias num comentário curto. Não comente o óbvio.
- Não adicione dependência sem justificar o impacto em manutenção.
- Todo `service/` novo precisa de teste unitário.
- Respeite a fronteira da feature: não acople features fora das dependências declaradas.
- Ao terminar, rode `./mvnw test` e relate o resultado.

## Commits e PR

- Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`).
- Branch por feature (`feature/nome`). Um PR resolve uma coisa só.