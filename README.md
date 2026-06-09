# Personal Financial Control

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-relacional-blue)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-yellow)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

> API REST para controle de finanças pessoais — contas, categorias, transações, orçamentos e relatórios de agregação.

## Descrição

O **Personal Financial Control** é a evolução de uma planilha de controle
financeiro para uma API REST versionada, testável e deployável. Ele gerencia
contas, categorias, transações e orçamentos, e expõe endpoints de agregação
para responder perguntas como "quanto gastei por categoria neste mês?" e
"como está o orçado vs. realizado?".

Não há interface gráfica neste escopo: a entrega é a API documentada via
OpenAPI, pronta para ser consumida por qualquer frontend.

## Status do Projeto

Em desenvolvimento. O núcleo de features está sendo construído seguindo as
fases descritas no [roadmap](#roadmap).

## Tecnologias

- **Java 21** (LTS)
- **Spring Boot 3.x** — Spring Web, Spring Data JPA, Spring Security
- **PostgreSQL** — banco relacional
- **Flyway** — versionamento de schema
- **Bean Validation (Jakarta)** — validação na borda
- **JWT (jjwt)** + **BCrypt** — autenticação e hash de senhas
- **Bucket4j** — limitação de taxa (rate limiting)
- **Lombok** — redução de boilerplate em entidades e DTOs
- **springdoc-openapi** — documentação Swagger UI
- **Docker Compose** — Postgres local
- **Testcontainers** — testes de integração com Postgres real
- **Render** — deploy

## Arquitetura

O código é organizado **por feature** (`package-by-feature`): cada
funcionalidade — conta, categoria, transação, orçamento — é um pacote
autocontido com suas próprias camadas (controller, service, repository,
entidade, DTO). O detalhamento das decisões está em
[`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Como Instalar e Rodar

### Pré-requisitos

- Java 21
- Docker e Docker Compose

### Passos

```bash
# 1. Clonar o repositório
git clone https://github.com/seu-usuario/prj-personal-financial-control.git
cd prj-personal-financial-control/pfc

# 2. Subir o PostgreSQL local
docker compose up -d

# 3. Rodar com o perfil local (credenciais já configuradas em application-local.yaml)
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`.

## Endpoints da API

Todas as rotas de recurso são versionadas sob `/api/v1` e exigem autenticação
(`Authorization: Bearer <token>`), exceto as de `/api/v1/auth`. Cada usuário só
acessa os próprios dados — ver [`SECURITY.md`](./SECURITY.md).

### Autenticação (`/api/v1/auth`) — públicas

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/v1/auth/register` | Registra um novo usuário e retorna um token de acesso |
| `POST` | `/api/v1/auth/login` | Autentica com e-mail e senha e retorna um token de acesso |

### Recursos financeiros — autenticadas

| Método | Rota | Descrição |
|--------|------|-----------|
| `GET` | `/api/v1/accounts` | Lista as contas do usuário autenticado |
| `POST` | `/api/v1/accounts` | Cria uma conta |
| `GET` | `/api/v1/categories` | Lista as categorias do usuário autenticado |
| `POST` | `/api/v1/categories` | Cria uma categoria |
| `GET` | `/api/v1/transactions` | Lista transações (com filtros por período/conta/categoria) |
| `POST` | `/api/v1/transactions` | Registra uma receita ou despesa |
| `GET` | `/api/v1/budgets` | Lista os orçamentos por categoria/mês |
| `POST` | `/api/v1/budgets` | Define um orçamento |
| `GET` | `/api/v1/reports/spending?month=YYYY-MM` | Gastos agregados por categoria no mês |
| `GET` | `/api/v1/reports/budget-comparison?month=YYYY-MM` | Orçado vs. realizado no mês |
| `GET` | `/api/v1/reports/account-balances` | Saldo atual por conta |

> Cada recurso também expõe `PUT /{id}` e `DELETE /{id}`. A lista completa e
> interativa está sempre disponível no Swagger UI (ver "Documentação da API").

## Variáveis de Ambiente

**Desenvolvimento local:** use o perfil `local` — as credenciais ficam em
`application-local.yaml` (ignorado pelo Git). Nenhuma variável de ambiente é
necessária.

**Produção / CI:** a aplicação lê as credenciais do ambiente. Nunca as coloque
no código ou no Git.

```bash
# Conexão de runtime (role de privilégio mínimo — ver SECURITY.md > Banco de dados)
DB_URL=jdbc:postgresql://<host>:5432/pfc
DB_USERNAME=<usuario-runtime>
DB_PASSWORD=<senha-runtime>

# Conexão dedicada do Flyway (role dona do schema, com privilégio de DDL)
FLYWAY_URL=jdbc:postgresql://<host>:5432/pfc   # opcional; usa DB_URL se omitida
FLYWAY_USERNAME=<usuario-dono-do-schema>
FLYWAY_PASSWORD=<senha-dono-do-schema>

# Autenticação (Spring Security + JWT — ver SECURITY.md > Autenticação e autorização)
JWT_SECRET=<segredo-hmac-sha-256-bits-minimo>
JWT_EXPIRATION_MS=3600000                      # opcional; padrão 1h

# CORS — domínio(s) reais do frontend, nunca "*" (ver SECURITY.md > CORS)
CORS_ALLOWED_ORIGINS=https://meu-frontend.com

# Limitação de taxa — opcionais; valores abaixo são os padrões
RATE_LIMIT_LOGIN_CAPACITY=5
RATE_LIMIT_LOGIN_REFILL_TOKENS=5
RATE_LIMIT_LOGIN_REFILL_PERIOD_SECONDS=60
RATE_LIMIT_DEFAULT_CAPACITY=60
RATE_LIMIT_DEFAULT_REFILL_TOKENS=60
RATE_LIMIT_DEFAULT_REFILL_PERIOD_SECONDS=60
```

## Documentação da API

Com a aplicação rodando, a documentação interativa (Swagger UI) fica em:

```
http://localhost:8080/swagger-ui.html
```

## Segurança

As práticas e o roadmap de segurança estão em
[`SECURITY.md`](./SECURITY.md). A API exige autenticação (Spring Security +
JWT) e isola os dados por usuário (autorização por dono) — registre-se em
`POST /api/v1/auth/register`, autentique-se em `POST /api/v1/auth/login` e use
o token retornado no header `Authorization: Bearer <token>` nas demais rotas.

## Roadmap

1. **Fundação** — projeto, configuração, Docker Compose do Postgres, módulo
   compartilhado (config + handler de erro), primeira migration.
2. **Features independentes** — `account` e `category` em paralelo, depois
   `transaction` e `budget`.
3. **Convergência** — `report`, documentação OpenAPI e deploy.
4. **Hardening** — autenticação (Spring Security + JWT), autorização por dono,
   cabeçalhos HTTP de segurança, usuário de banco de privilégio mínimo,
   limitação de taxa (Bucket4j) e Testcontainers nos testes de integração.

Evoluções previstas: paginação e filtros nas listagens, exportação de
relatórios.

## Como Contribuir

1. Faça um fork do projeto.
2. Crie uma branch para sua mudança (`git checkout -b feature/minha-feature`).
3. Faça commit (`git commit -m "feat: descrição"`).
4. Abra um Pull Request.

## Licença

Distribuído sob a licença MIT. Veja o arquivo `LICENSE` para detalhes.
