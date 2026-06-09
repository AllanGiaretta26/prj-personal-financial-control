# ADR-009: Limitação de taxa com Bucket4j (buckets em memória, chave por IP/usuário)

**Status:** Accepted
**Data:** 2026-06-08

## Contexto

`SECURITY.md` previa, na seção `[Planejado]`, "proteção contra abuso e força bruta no login (quando a auth existir): limitar requisições por IP/usuário, via biblioteca (ex.: Bucket4j) ou no proxy do Render". Com o pacote `auth` implementado (`ADR-008`) e o endpoint `/api/v1/auth/login` existindo, esse momento chegou — um endpoint de login sem limite de tentativas é um alvo direto de ataques de força bruta e *credential stuffing*.

Havia também uma decisão de granularidade a tomar: aplicar um único limite genérico a toda a API, ou diferenciar o perfil de limite por tipo de rota (rotas de autenticação, alvo de força bruta, vs. uso normal da API por um usuário já autenticado).

## Decisão

Adotamos **Bucket4j** (`com.bucket4j:bucket4j_jdk17-core:8.14.0`) com buckets **em memória** (`ConcurrentHashMap<String, Bucket>`, encapsulado em `RateLimitBucketProvider`), aplicados por um filtro próprio (`RateLimitFilter`, `OncePerRequestFilter`) registrado na `SecurityFilterChain` **antes** de `JwtAuthenticationFilter`.

### Dois perfis de limite, com chaves diferentes

- **Rotas de autenticação** (`/api/v1/auth/login`, `/api/v1/auth/register`): perfil **estrito** (`rate-limit.login.*`, padrão 5 requisições/minuto), sempre com chave **por IP** (`"ip:" + remoteAddr`) — são rotas públicas, não há identidade de usuário disponível, e são justamente o alvo de força bruta que a proteção visa conter.
- **Demais rotas `/api/v1/**`**: perfil **generoso** (`rate-limit.default.*`, padrão 60 requisições/minuto), com chave **por usuário** (`"user:" + email`, extraído do JWT) quando há um token presente e válido, e por IP como *fallback* quando não há (token ausente, malformado, expirado ou com assinatura inválida — a requisição ainda vai receber 401 do Spring Security, mas precisa ser contabilizada para conter enchentes de tráfego não autenticado).

Ambos os limites — capacidade, tokens de recarga e período — são configuráveis via `application.yaml` (`rate-limit.login.*` / `rate-limit.default.*`, com variáveis de ambiente `RATE_LIMIT_*`), seguindo o padrão `${VAR:default}` já adotado no projeto.

### Posição na cadeia de filtros e extração de identidade

`RateLimitFilter` roda **antes** de `JwtAuthenticationFilter` por dois motivos:

1. Precisa proteger as rotas públicas de auth, que nunca alcançam o filtro JWT.
2. Requisições que estouram o limite devem ser rejeitadas o quanto antes — antes de gastar trabalho com parsing de credenciais, autenticação ou acesso ao banco.

Como consequência, o `SecurityContextHolder` ainda não está populado quando o filtro roda — por isso `RateLimitFilter` extrai e valida o JWT **diretamente via `JwtService.extractEmail`**, de forma independente do `JwtAuthenticationFilter` (uma pequena duplicação de "ler o header `Authorization`", aceita em troca de rodar mais cedo na cadeia).

Excedido o limite, o filtro responde `429 Too Many Requests` com corpo `ProblemDetail` (mesmo formato RFC 7807 do `GlobalExceptionHandler`, serializado diretamente via `ObjectMapper` — mesma técnica de `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`) e cabeçalho `Retry-After` calculado a partir de `ConsumptionProbe.getNanosToWaitForRefill()`.

### `X-Forwarded-For` deliberadamente não confiável

O filtro usa exclusivamente `HttpServletRequest.getRemoteAddr()` para identificar o IP do cliente — **não** lê `X-Forwarded-For`. Esse header só é confiável quando a aplicação está atrás de um proxy reverso que o reescreve a cada salto (cenário de produção, onde a plataforma de deploy termina TLS e repassa as requisições). Sem uma lista de proxies confiáveis configurada (ex.: CIDRs do Render), confiar no header permitiria que o **próprio cliente forjasse seu IP** enviando um `X-Forwarded-For` arbitrário — um vetor direto de bypass do rate limit. Como a maioria das proteções desta fase, fica registrado como ponto a revisitar se/quando a topologia de proxy reverso em produção for conhecida e configurável.

### Buckets em memória, não distribuídos

Optamos por um `ConcurrentHashMap` local em vez de um backend distribuído (Redis, Hazelcast — também suportados pelo Bucket4j via módulos de integração). Isso é suficiente para o estágio atual do projeto (instância única) e evita uma dependência operacional adicional (um Redis a provisionar, monitorar e manter disponível só para rate limiting).

## Consequências

**Positivas:**
- Fecha o item `[Planejado]` "Limitação de taxa" de `SECURITY.md`, com proteção concreta contra força bruta no login — o cenário que a seção explicitamente visava.
- Diferenciar por perfil (estrito/IP para auth, generoso/usuário para o resto) equilibra segurança (conter força bruta num endpoint sensível) com usabilidade (não pena usuários legítimos com tráfego normal).
- Resposta `429` no mesmo formato `ProblemDetail` das demais respostas de erro mantém a API consistente para quem a consome — sem um formato de erro especial só para rate limiting.
- Sem dependência operacional nova (nenhum Redis/Hazelcast a provisionar) — Bucket4j em memória é uma adição de biblioteca, não de infraestrutura.

**Negativas / trade-offs:**
- **Buckets não compartilhados entre instâncias**: se a aplicação rodar com múltiplas réplicas (escalonamento horizontal), cada instância mantém seus próprios buckets — o limite efetivo por cliente passa a ser, na prática, `limite_configurado × número_de_réplicas`. Aceitável hoje (instância única); revisitar (Bucket4j + Redis, ou rate limiting no proxy/CDN) se/quando o projeto escalar horizontalmente.
- **`X-Forwarded-For` ignorado**: em produção, atrás de um proxy reverso, todas as requisições de fato chegam de um único IP de borda — o que tornaria o limite "por IP" efetivamente um limite global, a menos que uma lista de proxies confiáveis seja configurada para extrair o IP real do cliente. Esse ajuste fica para quando a topologia de produção (Render) for conhecida em detalhe.
- **Chave por e-mail extraído do token, sem validar assinatura contra o `SecurityContext`**: o `RateLimitFilter` confia no e-mail que `JwtService.extractEmail` devolve de um token estruturalmente válido (assinatura/expiração conferidas por `JwtService`), mas roda antes do `JwtAuthenticationFilter` popular o `SecurityContextHolder`. Isso é intencional (ver "Posição na cadeia" acima) — o pior caso de uma falha aqui é apenas uma chave de bucket "errada", nunca uma falha de autenticação (o `JwtAuthenticationFilter`, mais adiante na cadeia, continua sendo a única fonte de verdade para autenticação).
