# ADR-006: Lombok em entidades e DTOs

**Status:** Accepted
**Data:** 2026-06-07

## Contexto

Entidades JPA e DTOs concentravam uma quantidade significativa de código boilerplate (getters, setters e construtores all-args escritos à mão), repetido de forma quase idêntica em todas as features (`account`, `category`, `transaction`, `budget`). Isso aumentava o tamanho dos arquivos sem agregar valor de negócio e elevava o custo de manutenção sempre que um campo era adicionado, removido ou renomeado.

## Decisão

Adotar a biblioteca [Lombok](https://projectlombok.org/) (dependência `org.projectlombok:lombok`, escopo opcional/`provided`) para gerar esse código em tempo de compilação:

- **Entidades JPA** (`Account`, `Category`, `Transaction`, `Budget`): `@Getter @Setter` no nível da classe.
- **DTOs de request**: `@Getter @Setter`, mantendo as anotações de validação (`@NotNull`, `@Size`, etc.) nos campos.
- **DTOs de response**: `@Getter @AllArgsConstructor`, preservando o padrão de objeto imutável construído via construtor all-args já documentado em "DTO conversion".

Deliberadamente **não** usamos `@Data` em entidades JPA: essa anotação gera `equals`/`hashCode`/`toString` sobre todos os campos, o que é problemático em entidades com associações `@ManyToOne` carregadas via `FetchType.LAZY` — pode disparar lazy-loading inesperado, gerar recursão entre entidades associadas, e quebrar `equals`/`hashCode` em proxies do Hibernate.

## Consequências

**Positivas:**
- Reduz significativamente o volume de código repetitivo nas entidades e DTOs, deixando visível apenas o que importa: campos e anotações de mapeamento/validação.
- Adicionar, remover ou renomear um campo passa a exigir uma única alteração (no campo), sem tocar em getters/setters/construtores.
- `@AllArgsConstructor` nos DTOs de response elimina a inconsistência que existia entre `AccountResponse`/`CategoryResponse` (já usavam construtor all-args) e `TransactionResponse`/`BudgetResponse` (eram montados via `new` + setters).

**Negativas / trade-offs:**
- Reverte a convenção anterior do projeto ("no Lombok" em entidades), documentada agora como obsoleta nesta ADR e atualizada no `CLAUDE.md`.
- Exige plugin de anotação do Lombok no IDE para que o autocomplete reconheça os métodos gerados (a compilação via Maven funciona sem o plugin, pois o annotation processor roda no `javac`).
- Código gerado em tempo de compilação é "mágico": getters/setters não aparecem no fonte, o que pode dificultar a leitura para quem não conhece Lombok. Mitigado pelo uso restrito a `@Getter`/`@Setter`/`@AllArgsConstructor` — anotações simples e amplamente conhecidas — evitando formas mais opacas como `@Data` ou `@Builder` nas entidades.
