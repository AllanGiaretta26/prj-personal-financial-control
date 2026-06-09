# ADR-008: Autenticação com Spring Security + JWT e modelo de ownership por usuário

**Status:** Accepted
**Data:** 2026-06-08

## Contexto

`ADR-004` excluiu autenticação do escopo do MVP deliberadamente, antecipando que a arquitetura package-by-feature permitiria adicionar um pacote `auth` no futuro "sem reescrever as features existentes". Esse momento chegou: `SECURITY.md` já descrevia, na seção `[Planejado]`, o contrato esperado — "login emite um token; endpoints protegidos exigem o token no header `Authorization: Bearer`" — e o aviso de exposição ("a API não tem autenticação... só dados fictícios") precisava ser fechado antes de qualquer dado financeiro real trafegar pelo sistema.

Duas decisões de design precisavam ser tomadas em conjunto, pois uma decorre da outra:

1. **Como autenticar** — qual mecanismo emite e valida a identidade em cada requisição.
2. **Como autorizar** — uma vez identificado o usuário, como garantir que ele só acesse os próprios dados financeiros (contas, categorias, transações, orçamentos), e não os de terceiros.

## Decisão

### 1. Autenticação: Spring Security + JWT via jjwt

Adotamos **Spring Security 6** com um filtro JWT customizado (`JwtAuthenticationFilter`, um `OncePerRequestFilter`) em vez do módulo `spring-boot-starter-oauth2-resource-server`. A geração/validação de tokens usa a biblioteca **jjwt** (`io.jsonwebtoken:jjwt-api/-impl/-jackson`, versão 0.12.6), com assinatura HMAC-SHA256 e segredo injetado via `${JWT_SECRET}` (nunca no repositório — ver `SECURITY.md` > Gestão de segredos).

Optamos por jjwt + filtro próprio em vez do Resource Server porque:

- O contrato exigido é simples e está totalmente sob nosso controle: "login emite um token opaco para o cliente; endpoints exigem `Authorization: Bearer <token>`". Não há necessidade de federação com um Authorization Server externo, JWKS remoto, ou múltiplos emissores — exatamente o cenário para o qual o Resource Server (pensado para validar tokens emitidos *por terceiros*) adiciona complexidade sem benefício.
- jjwt é uma biblioteca pequena e amplamente adotada, com API direta para emitir e validar tokens HMAC — controle total sobre claims, expiração e mensagens de erro, sem a camada de configuração adicional (`issuer-uri`/`jwk-set-uri`) que o Resource Server pressupõe.
- Um filtro próprio se encaixa no padrão arquitetural já estabelecido pelo projeto (`shared/exception/GlobalExceptionHandler` converte exceções para `ProblemDetail` RFC 7807) — `RestAuthenticationEntryPoint` e `RestAccessDeniedHandler` serializam `ProblemDetail` diretamente via `ObjectMapper`, mantendo o mesmo formato de erro em toda a API, inclusive nas camadas que rodam fora do `DispatcherServlet`.

Componentes do pacote `com.pfc.auth`:

- **`User`** (entidade JPA, migration `V2__create_users.sql`) + `UserRepository`.
- **`AuthController`** (`/api/v1/auth/register`, `/api/v1/auth/login`) e **`AuthService`** (registro com e-mail único + hash `BCryptPasswordEncoder`; login via `AuthenticationManager`, emite JWT).
- **`JwtService`** (geração/parsing/validação) e **`CustomUserDetailsService`** (`UserDetailsService` por e-mail).
- **`SecurityConfig`**: sessão `STATELESS`, CSRF desabilitado (API sem cookies de sessão — não há superfície para CSRF clássico), `/api/v1/auth/**` e paths do Swagger liberados, restante exige autenticação. Também é onde os **cabeçalhos HTTP de segurança** (`X-Content-Type-Options`, `X-Frame-Options: DENY`, HSTS com `includeSubDomains` e `max-age` de 1 ano) e a política de **CORS restrita por configuração** (`app.cors.allowed-origins`, nunca `*`) são habilitados — fechando, na mesma mudança, três itens que estavam `[Planejado]` em `SECURITY.md`.

Senhas usam **BCrypt** (`BCryptPasswordEncoder`), conforme já previsto em `SECURITY.md` ("hash forte (BCrypt/Argon2), nunca em texto puro").

### 2. Ownership: `user_id` explícito nas quatro tabelas + padrão "404, nunca 403"

Cada recurso financeiro (`account`, `category`, `transaction`, `budget`) passa a pertencer a exatamente um usuário. Avaliamos duas formas de modelar isso:

- **(a) `user_id` explícito em cada uma das quatro tabelas** — toda query de listagem/busca filtra diretamente por `WHERE user_id = :currentUserId`, sem depender de joins através de outras entidades.
- **(b) Ownership transitivo** — por exemplo, `Transaction`/`Budget` herdariam o dono através de `Account`/`Category`, evitando duplicar a coluna.

Escolhemos **(a)**, confirmada explicitamente com o usuário (decisão de produto: cada usuário mantém sua própria lista de categorias, não uma lista compartilhada). Migration `V3__add_user_ownership.sql` adiciona `user_id UUID NOT NULL REFERENCES users(id)` às quatro tabelas — `NOT NULL` direto é seguro porque a base local está vazia (projeto pré-dados-reais, conforme `ADR-004`/`SECURITY.md`).

Razões para (a) sobre (b):

- **Isolamento sem ambiguidade**: o dono de cada linha é uma coluna própria, não uma inferência via join — elimina por completo a possibilidade de um bug de relacionamento (FK trocada, join incorreto) vazar dados entre usuários.
- **Queries simples e auditáveis**: `WHERE user_id = :currentUserId` é direto de ler, indexar e revisar; ownership transitivo exigiria joins em cada consulta e uma regra implícita ("o dono de uma transação é o dono da conta dela") fácil de esquecer ao adicionar uma nova query.
- **Modelo de domínio mais correto**: cada usuário tem sua própria lista de categorias — refletir isso como uma FK direta em `category.user_id` é mais fiel ao domínio do que derivar posse por transitividade.

Para evitar duplicar em quatro services a lógica de "resolver o usuário autenticado a partir do `SecurityContext`", criamos **`AuthenticatedUserProvider`** (`com.pfc.auth`) — ponto único injetado em `AccountService`, `CategoryService`, `TransactionService`, `BudgetService` (e também em `ReportService`, ver abaixo). Cada repositório ganhou variantes *owner-scoped* (`findAllByOwner`, `findByIdAndOwner`, `existsByIdAndOwner`), que colapsam "o recurso existe" + "pertence a mim" em uma única consulta.

**Padrão de resposta a acesso cruzado: sempre 404, nunca 403.** Quando o usuário A tenta acessar, alterar ou apagar um recurso do usuário B, a resposta é `ResourceNotFoundException` → HTTP 404 — exatamente a mesma resposta de "o recurso não existe". Retornar 403 ("Forbidden") confirmaria *a existência* do recurso, vazando informação (Insecure Direct Object Reference / IDOR — ex.: um atacante poderia enumerar IDs e descobrir quais existem no sistema, mesmo sem conseguir acessá-los). 404 é indistinguível de "esse ID nunca existiu", o que é a postura correta de privacidade.

A mesma lógica de owner-scoping se estende às **referências cruzadas**: ao criar/atualizar uma `Transaction` ou `Budget`, a conta/categoria referenciada é resolvida via `findByIdAndOwner` (helpers privados `findOwnedAccount`/`findOwnedCategory`) — um usuário não pode, por exemplo, registrar uma transação contra a conta de outro usuário (o que vazaria, indiretamente, a existência e o ID daquele recurso).

### 3. Extensão além do escopo original: `ReportService`

Durante a implementação, identificamos que `ReportService` (que não tem entidade própria — agrega dados das outras quatro features via streams em memória, conforme `ADR-005`) ficaria, sem ajuste, agregando e expondo dados financeiros de **todos** os usuários, não apenas do usuário autenticado — uma forma sutil, porém grave, de vazamento entre contas. `ReportService` também passou a injetar `AuthenticatedUserProvider` e a escopar os três relatórios (`spendingByCategory`, `budgetComparison`, `accountBalances`) pelo usuário autenticado, usando os repositórios *owner-scoped* já criados para as outras features.

## Consequências

**Positivas:**
- Fecha o aviso de exposição de `SECURITY.md` ("API não tem autenticação... só dados fictícios") — a API agora pode, em princípio, lidar com dados financeiros reais de múltiplos usuários com isolamento garantido na camada de persistência (não apenas na de apresentação).
- `user_id` explícito torna o isolamento auditável linha a linha — qualquer query de relatório, auditoria ou migração futura pode confirmar posse sem reconstruir cadeias de relacionamento.
- O padrão "404, nunca 403" se torna uma regra simples e uniforme aplicável a qualquer feature nova: resolver por `findByIdAndOwner` (ou equivalente) e deixar `ResourceNotFoundException` cuidar do resto — sem necessidade de checagens de permissão espalhadas pelos controllers/services.
- `AuthenticatedUserProvider` evita a duplicação de "como obter o usuário atual" em cinco services (quatro features + report), e oferece um único lugar para evoluir essa lógica (ex.: cache, suporte a impersonação administrativa futura).

**Negativas / trade-offs:**
- **Coluna repetida em quatro tabelas**: `user_id` é redundante em `transaction`/`budget` quando comparado a um modelo de ownership transitivo — um trade-off consciente em favor de simplicidade de consulta e auditabilidade (ver "Razões para (a)" acima).
- **Migration `NOT NULL` direta**: só é segura porque a base local está vazia; em um cenário com dados reais pré-existentes, a migration precisaria de um passo intermediário (coluna nullable → backfill → `NOT NULL`).
- **Acoplamento ao `SecurityContextHolder`**: `AuthenticatedUserProvider` lança `IllegalStateException` se chamado fora de uma requisição autenticada — aceitável porque, atrás do filtro JWT em endpoints protegidos, isso "não deveria ocorrer"; indicaria uso incorreto (ex.: chamado de um job em background sem contexto de segurança).
- **Acoplamento ao Spring Security**: jjwt + filtro próprio significa que, se no futuro for necessário validar tokens emitidos por um Identity Provider externo (Auth0, Keycloak, Cognito), será preciso migrar para o Resource Server — uma reescrita do pacote `auth`, não uma extensão incremental. Aceitamos esse risco porque o cenário atual (emissor único, sob nosso controle) não justifica a complexidade adicional hoje.
