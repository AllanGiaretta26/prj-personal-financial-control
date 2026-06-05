# ADR-003: UUID como chave primária em todas as entidades

**Status:** Accepted  
**Data:** 2026-06-05

## Contexto

Ao definir o tipo de chave primária das entidades, as opções principais eram `BIGINT` sequencial gerado pelo banco (auto-increment / sequence) ou `UUID`. A escolha impacta segurança da API, testabilidade e eficiência de armazenamento.

## Decisão

Usar `UUID` como chave primária em todas as entidades, com geração no lado da aplicação via `@GeneratedValue(strategy = GenerationType.UUID)` do Hibernate 6+.

## Consequências

**Positivas:**
- Não expõe a contagem de registros na API: IDs sequenciais permitem inferir o volume total de dados (`/transactions/1` vs `/transactions/999`).
- O ID pode ser gerado pela aplicação antes de persistir, eliminando round-trip ao banco — simplifica testes unitários sem banco de dados.
- IDs globalmente únicos simplificam integrações futuras ou eventual merge de dados de múltiplas fontes.

**Negativas / trade-offs:**
- UUIDs ocupam mais espaço (16 bytes) comparado a `BIGINT` (8 bytes).
- Índices B-tree sobre UUIDs aleatórios têm fragmentação maior do que sobre inteiros sequenciais, o que pode impactar performance de escrita em alto volume. Para o volume pessoal do PFC esse trade-off é irrelevante.
