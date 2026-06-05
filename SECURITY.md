# Política e Práticas de Segurança — Personal Financial Control

> Descreve como o projeto trata segurança: como reportar falhas, quais
> práticas estão em vigor e quais estão planejadas. Como o projeto está em
> desenvolvimento, cada prática abaixo está marcada como **[Em vigor]**
> (decidida na arquitetura) ou **[Planejado]** (alvo antes de dados reais).

---

## Versões suportadas

| Versão | Suporte de segurança |
|---|---|
| `main` (em desenvolvimento) | Sim |
| Releases anteriores | Não há ainda |

---

## Como reportar uma vulnerabilidade

Não abra issue pública para falhas de segurança — isso expõe o problema antes
da correção. Reporte de forma privada por e-mail para **[seu-email]**,
incluindo passos para reproduzir e o impacto observado. O retorno é dado assim
que a falha for avaliada.

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

## Autenticação e autorização · [Planejado]

> **Aviso de exposição.** No estado atual a API **não tem autenticação**.
> Enquanto for assim, ela só deve ir ao ar com **dados fictícios de
> demonstração**. Nenhum dado financeiro real antes da auth estar no lugar.

Plano (pacote `auth` dedicado, sem reescrever as features existentes):

- **Spring Security + JWT**: login emite um token; endpoints protegidos exigem
  o token no header `Authorization: Bearer`.
- Cada recurso financeiro passa a pertencer a um usuário; um usuário só acessa
  os próprios dados (autorização por dono).
- Senhas com **hash forte** (BCrypt/Argon2), nunca em texto puro.

---

## Validação de entrada e injeção · [Em vigor]

- **Bean Validation** rejeita entrada inválida na borda (Controller), antes de
  chegar à regra de negócio.
- **SQL Injection:** o Spring Data JPA parametriza as queries por padrão. O
  risco mora em *queries nativas com concatenação de string* — proibido montar
  SQL com input do usuário; usar sempre parâmetros nomeados/posicionais.

---

## CORS · [Planejado]

- Em produção, restringir `allowed-origins` ao domínio do frontend conhecido.
- **Nunca** `*` em produção — origem aberta permite que qualquer site faça
  requisições autenticadas em nome do usuário.

---

## Cabeçalhos de segurança HTTP · [Planejado]

Ao adicionar o Spring Security, habilitar os cabeçalhos padrão de proteção
(`X-Content-Type-Options`, `X-Frame-Options`, HSTS). Eles instruem o navegador
a bloquear classes inteiras de ataque (MIME sniffing, clickjacking).

---

## Banco de dados · [Em vigor / Planejado]

- **[Planejado]** A API conecta com um usuário de banco de **privilégio
  mínimo** — acesso de leitura/escrita às tabelas da aplicação, sem permissão
  administrativa (criar/dropar banco).
- **[Em vigor]** O **Flyway** versiona o schema; mudanças passam por migration
  revisada, não por alteração manual em produção.
- **[Em vigor]** Conexão sempre via SSL.

---

## Dependências · [Planejado]

- Manter dependências atualizadas — CVEs novos surgem em bibliotecas conhecidas.
- Habilitar o **Dependabot** (alertas automáticos de dependência vulnerável no
  GitHub) e, opcionalmente, o **OWASP Dependency-Check** no build.

---

## Exposição de dados e logs · [Em vigor]

- **Erros não vazam detalhes internos.** As respostas usam `ProblemDetail` sem
  stacktrace; em produção, `server.error.include-stacktrace=never`.
- **Logs não contêm dados sensíveis** — não logar senhas, tokens nem valores
  que identifiquem o usuário em texto puro.

---

## Limitação de taxa (rate limiting) · [Planejado]

Proteção contra abuso e força bruta no login (quando a auth existir): limitar
requisições por IP/usuário, via biblioteca (ex.: Bucket4j) ou no proxy do
Render.

---

## Checklist de deploy seguro

Antes de cada deploy em produção:

- [ ] Nenhum segredo no código ou no histórico do Git
- [ ] Variáveis de ambiente configuradas no painel do Render
- [ ] `DATABASE_URL` com `sslmode=require`
- [ ] Usuário do banco com privilégio mínimo
- [ ] CORS restrito ao domínio do frontend (sem `*`)
- [ ] Stacktrace desativado na resposta de erro
- [ ] Dependências sem CVE crítico em aberto
- [ ] Se há dado real exposto, a autenticação está ativa
