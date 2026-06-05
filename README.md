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
- **Spring Boot 3.x** — Spring Web, Spring Data JPA
- **PostgreSQL** — banco relacional
- **Flyway** — versionamento de schema
- **Bean Validation (Jakarta)** — validação na borda
- **springdoc-openapi** — documentação Swagger UI
- **Docker Compose** — Postgres local
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

Contrato previsto (em construção):

| Método | Rota | Descrição |
|--------|------|-----------|
| `GET` | `/api/accounts` | Lista as contas |
| `POST` | `/api/accounts` | Cria uma conta |
| `GET` | `/api/categories` | Lista as categorias |
| `POST` | `/api/categories` | Cria uma categoria |
| `GET` | `/api/transactions` | Lista transações (com filtros por período/conta/categoria) |
| `POST` | `/api/transactions` | Registra uma receita ou despesa |
| `GET` | `/api/budgets` | Lista os orçamentos por categoria/mês |
| `POST` | `/api/budgets` | Define um orçamento |
| `GET` | `/api/reports/spending-by-category` | Gastos agregados por categoria |
| `GET` | `/api/reports/budget-vs-actual` | Orçado vs. realizado |
| `GET` | `/api/reports/balance-by-account` | Saldo por conta |

## Variáveis de Ambiente

**Desenvolvimento local:** use o perfil `local` — as credenciais ficam em
`application-local.yaml` (ignorado pelo Git). Nenhuma variável de ambiente é
necessária.

**Produção / CI:** a aplicação lê as credenciais do ambiente. Nunca as coloque
no código ou no Git.

```bash
DB_URL=jdbc:postgresql://<host>:5432/pfc
DB_USERNAME=<usuario>
DB_PASSWORD=<senha>
```

## Documentação da API

Com a aplicação rodando, a documentação interativa (Swagger UI) fica em:

```
http://localhost:8080/swagger-ui.html
```

## Segurança

As práticas e o roadmap de segurança estão em
[`SECURITY.md`](./SECURITY.md). Ponto de atenção: enquanto a autenticação não
estiver implementada, a API só deve ir ao ar com **dados fictícios de
demonstração** — nunca dados financeiros reais.

## Roadmap

1. **Fundação** — projeto, configuração, Docker Compose do Postgres, módulo
   compartilhado (config + handler de erro), primeira migration.
2. **Features independentes** — `account` e `category` em paralelo, depois
   `transaction` e `budget`.
3. **Convergência** — `report`, documentação OpenAPI e deploy.

Evoluções previstas: autenticação (Spring Security + JWT), paginação e filtros
nas listagens, exportação de relatórios.

## Como Contribuir

1. Faça um fork do projeto.
2. Crie uma branch para sua mudança (`git checkout -b feature/minha-feature`).
3. Faça commit (`git commit -m "feat: descrição"`).
4. Abra um Pull Request.

## Licença

Distribuído sob a licença MIT. Veja o arquivo `LICENSE` para detalhes.
