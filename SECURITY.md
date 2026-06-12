# Política e Práticas de Segurança — Personal Financial Control

> Descreve como o projeto trata segurança: como reportar falhas, quais
> práticas estão em vigor e quais estão planejadas. Como o projeto está em
> desenvolvimento, cada prática abaixo está marcada como **[Em vigor]**
> (decidida na arquitetura) ou **[Planejado]** (alvo antes de dados reais).

---

## Versões suportadas

| Versão | Suporte de segurança |
|---|---|
| `main` (em produção no Render) | Sim |
| `0.2.0` (release atual) | Sim |
| `< 0.2.0` | Não |

---

## Como reportar uma vulnerabilidade

Não abra issue pública para falhas de segurança — isso expõe o problema antes
da correção. Use o **canal privado de report do GitHub**: aba **Security** do
repositório → **Report a vulnerability** ([Private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)).
Inclua passos para reproduzir e o impacto observado. O retorno é dado assim que
a falha for avaliada.

---

## Princípios

O projeto adota **defesa em profundidade** (várias camadas independentes, para
que a falha de uma não comprometa tudo) e **menor privilégio** (cada parte
recebe apenas o acesso estritamente necessário). As seções a seguir são
aplicações concretas desses dois princípios.

---

## Gestão de segredos · [Em vigor]

Segredos (URL do banco, senha, futuras chaves de API) **nunca** vão para o
código nem para o Git.

- `.env` e qualquer arquivo de credencial ficam no `.gitignore`.
- Em produção, os segredos são injetados como **variáveis de ambiente** pelo
  painel do Render — não ficam no repositório.
- `application.yml` referencia variáveis (`${DB_PASSWORD}`), nunca valores.
- Se um segredo vazar (commit acidental), ele é **rotacionado** — trocar o
  valor, não apenas remover o commit, pois o histórico do Git é recuperável.

```yaml
# application.yml — referência, nunca o valor
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

---

## Transporte e TLS · [Em vigor]

- O Render provê **HTTPS/TLS automático** nos domínios `*.onrender.com`, então
  todo tráfego cliente↔API é criptografado sem configuração extra.
- A conexão da API com o Postgres usa **SSL** (`sslmode=require` na URL).

---

## Autenticação e autorização · [Em vigor]

Implementado no pacote `auth` (`com.pfc.auth`), sem reescrever as features
existentes — exatamente como antecipado em
`docs/adr/ADR-004-sem-autenticacao-no-mvp.md` (agora superseded por
`docs/adr/ADR-008-autenticacao-jwt-e-modelo-de-ownership.md`):

- **Spring Security + JWT** (biblioteca `jjwt`, HMAC-SHA256): `POST
  /api/v1/auth/register` e `POST /api/v1/auth/login` emitem um token;
  endpoints protegidos exigem o token no header `Authorization: Bearer
  <token>`. Sessão `STATELESS`, sem cookies — `/api/v1/auth/**` e os paths do
  Swagger são as únicas rotas públicas.
- **Autorização por dono**: `account`, `category`, `transaction` e `budget`
  têm `user_id` explícito (migration `V3__add_user_ownership.sql`); todo
  acesso é resolvido via `AuthenticatedUserProvider` e repositórios
  *owner-scoped* (`findByIdAndOwner` etc.) — um usuário nunca enxerga, lista,
  edita, apaga ou referencia dados de outro, **nem nos relatórios agregados**
  (`ReportService` também filtra pelo usuário autenticado).
- **Padrão "404, nunca 403" contra IDOR**: tentar acessar, alterar ou
  referenciar um recurso de outro usuário responde `404 Not Found` — a mesma
  resposta de "esse recurso não existe". Responder `403 Forbidden`
  confirmaria a existência do recurso para quem não deveria nem saber disso.
- **Senhas com hash forte**: `BCryptPasswordEncoder`, nunca em texto puro.

Detalhes e justificativa de design (escolha jjwt vs. Resource Server, modelo
`user_id` explícito vs. ownership transitivo, extensão do escopo a
`ReportService`) em `docs/adr/ADR-008-autenticacao-jwt-e-modelo-de-ownership.md`.

---

## Validação de entrada e injeção · [Em vigor]

- **Bean Validation** rejeita entrada inválida na borda (Controller), antes de
  chegar à regra de negócio.
- **SQL Injection:** o Spring Data JPA parametriza as queries por padrão. O
  risco mora em *queries nativas com concatenação de string* — proibido montar
  SQL com input do usuário; usar sempre parâmetros nomeados/posicionais.

---

## CORS · [Em vigor]

- Política configurada em `SecurityConfig` via `app.cors.allowed-origins`
  (variável de ambiente `CORS_ALLOWED_ORIGINS`, lista separada por vírgula).
  O padrão local cobre apenas hosts de desenvolvimento (`http://localhost:*`).
- Em produção, a variável **deve** ser sobrescrita com o(s) domínio(s) reais
  do frontend.
- **Nunca** `*` — origem aberta permite que qualquer site faça requisições
  autenticadas em nome do usuário. O código não aceita esse valor por padrão.

---

## Cabeçalhos de segurança HTTP · [Em vigor]

Habilitados em `SecurityConfig` junto com o Spring Security:

- **`X-Content-Type-Options: nosniff`** — bloqueia MIME sniffing.
- **`X-Frame-Options: DENY`** — bloqueia clickjacking (a página nunca pode ser
  embutida em `<iframe>`).
- **`Strict-Transport-Security` (HSTS)** — `includeSubDomains`, `max-age` de
  1 ano (31536000s). Só tem efeito sobre HTTPS (o navegador ignora o header em
  HTTP), mas já está ativo para quando a aplicação estiver atrás de TLS em
  produção (ver "Transporte e TLS").

---

## Banco de dados · [Em vigor]

- **[Em vigor]** A API conecta com um usuário de banco de **privilégio
  mínimo** — acesso de leitura/escrita às tabelas da aplicação, sem permissão
  administrativa (criar/dropar banco). Na prática, são **duas roles**: uma
  dona do schema (`pfc`), usada **apenas pelo Flyway** para aplicar migrations
  (precisa de DDL — criar/alterar tabelas), e uma role de runtime restrita
  (`pfc_app`), usada pela aplicação, com apenas
  `SELECT/INSERT/UPDATE/DELETE` nas tabelas e **nenhum** privilégio de DDL. A
  separação usa propriedades nativas do Spring
  (`spring.flyway.url/user/password`, que sobrepõem `spring.datasource.*` só
  na conexão de migration) e `ALTER DEFAULT PRIVILEGES` do Postgres, para que
  a role de runtime receba acesso automático a tabelas que o Flyway venha a
  criar no futuro. Detalhes e justificativa em
  `docs/adr/ADR-007-usuario-banco-privilegio-minimo.md`.
- **[Em vigor]** O **Flyway** versiona o schema; mudanças passam por migration
  revisada, não por alteração manual em produção.
- **[Em vigor]** Conexão sempre via SSL.

> **Em produção (Render):** o mesmo padrão de duas roles deve ser replicado —
> uma role dona do schema só para migrations e uma role restrita para
> runtime. As credenciais de cada uma são geradas e injetadas via variáveis de
> ambiente próprias no painel do Render: `DB_USERNAME`/`DB_PASSWORD` para a
> role restrita de runtime (usada por `spring.datasource.*`) e
> `FLYWAY_USERNAME`/`FLYWAY_PASSWORD` para a role dona do schema (usada por
> `spring.flyway.*`). Nenhuma delas usa os valores de conveniência do setup
> local.

---

## Dependências · [Em vigor]

- **[Em vigor]** **Alertas do Dependabot** habilitados no repositório GitHub —
  notificam automaticamente quando uma dependência passa a ter CVE conhecido.
  Sem CVE em aberto na última verificação.
- Manter dependências atualizadas — CVEs novos surgem em bibliotecas conhecidas.
- **[Planejado]** Adicionar `.github/dependabot.yml` (PRs automáticos de
  atualização) e, opcionalmente, o **OWASP Dependency-Check** no build.

---

## Exposição de dados e logs · [Em vigor]

- **Erros não vazam detalhes internos.** Toda exceção é traduzida pelo
  `GlobalExceptionHandler` para `ProblemDetail` (RFC 7807) sem stacktrace: as
  exceções de domínio e as do Spring MVC mantêm o status correto
  (`400`/`404`/`405`...), e qualquer exceção inesperada vira um `500` com
  mensagem genérica — o detalhe real é apenas **registrado no log do servidor**,
  nunca devolvido ao cliente.
- **Stacktrace desabilitado explicitamente.** Como defesa em profundidade,
  `server.error.include-stacktrace`, `include-message` e `include-binding-errors`
  estão fixados em `never` no `application.yaml` — a garantia não depende do
  default do framework. O endpoint interno `/error` também fica atrás de
  autenticação.
- **Logs não contêm dados sensíveis** — não logar senhas, tokens nem valores
  que identifiquem o usuário em texto puro.

---

## Limitação de taxa (rate limiting) · [Em vigor]

Implementado com **Bucket4j** (buckets em memória, token-bucket), via
`RateLimitFilter` registrado na cadeia de segurança antes do filtro JWT:

- **Rotas de autenticação** (`/api/v1/auth/login`, `/api/v1/auth/register`):
  perfil estrito por **IP** (`rate-limit.login.*`, padrão 5 req/min) — alvo
  clássico de força bruta e *credential stuffing*.
- **Demais rotas `/api/v1/**`**: perfil generoso por **usuário autenticado**
  (`rate-limit.default.*`, padrão 60 req/min), com IP como *fallback* quando
  não há token válido.
- Excesso responde `429 Too Many Requests` com corpo `ProblemDetail` (mesmo
  formato RFC 7807 do restante da API) e cabeçalho `Retry-After`.
- Limites configuráveis via `application.yaml` / variáveis `RATE_LIMIT_*`.
- O filtro **não confia** no header `X-Forwarded-For` (usa apenas
  `getRemoteAddr()`) — sem uma lista de proxies confiáveis configurada,
  confiar nesse header permitiria que o próprio cliente forjasse seu IP e
  contornasse o limite.

Detalhes e trade-offs (buckets em memória vs. distribuídos, granularidade por
perfil, não confiar em `X-Forwarded-For`) em
`docs/adr/ADR-009-rate-limiting-com-bucket4j.md`.

---

## Validação de segurança · [Em vigor]

Os controles acima são verificados de forma reproduzível, não apenas por
inspeção de código:

- **Testes de integração** (Testcontainers, Postgres real) exercitam a cadeia
  de segurança ponta a ponta com a `SecurityFilterChain` ativa: `AuthControllerIT`,
  `RateLimitFilterIT` e os `*ControllerIT` de cada feature cobrem autenticação,
  isolamento por dono e rate limiting. Rodam com `./mvnw test`.
- **Smoke test E2E** via **Postman CLI**: a coleção *"PFC v2 — Validação de
  Segurança"* (pastas numeradas 00→05) valida, contra a aplicação no ar, cada
  controle desta política:
  - **00–01** — emissão de JWT, login inválido → 401 e acesso sem token → 401
    (corpo `ProblemDetail`, sem vazar stacktrace);
  - **02** — CRUD autenticado do próprio dono;
  - **03** — isolamento entre usuários: ler/editar/apagar recurso alheio →
    **404 (nunca 403)**, sem expor o recurso;
  - **04** — rate limit das rotas de auth → **429** com `Retry-After`;
  - **05** — cabeçalhos `X-Content-Type-Options`/`X-Frame-Options` e acesso
    público ao Swagger/OpenAPI.
- A coleção roda pelo script `pfc/run-postman.ps1`, que garante uma execução
  **determinística** (reset do banco + reinício da app, zerando os buckets de
  rate limit em memória) e retorna *exit code* ≠ 0 se algum teste falhar —
  pronto para uso em CI.

---

## Checklist de deploy seguro

Antes de cada deploy em produção:

- [x] Nenhum segredo no código ou no histórico do Git (`create-app-role.sql` gitignorado)
- [x] Variáveis de ambiente configuradas no painel do Render
- [x] `DB_URL` / `FLYWAY_URL` com `sslmode=require` (verificado: app sobe e conecta com SSL exigido)
- [x] Usuário do banco com privilégio mínimo (`pfc_app` runtime; validado no smoke test E2E)
- [x] CORS sem `*` (default restrito; sobrescrever com o domínio do frontend quando publicado)
- [x] Stacktrace desativado na resposta de erro (`server.error.include-stacktrace=never` + `GlobalExceptionHandler`)
- [x] Dependências sem CVE crítico em aberto (alertas Dependabot ativos, 0 em aberto)
- [x] Se há dado real exposto, a autenticação está ativa
- [x] Suíte de validação de segurança passando (testes de integração + `pfc/run-postman.ps1`)
