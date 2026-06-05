# ADR-005: Feature `report` sem entidade JPA

**Status:** Accepted  
**Data:** 2026-06-05

## Contexto

A feature `report` expõe dados agregados (totais por categoria, balanço mensal, orçado vs. realizado). A alternativa natural seria criar uma entidade JPA `Report` respaldada por tabela ou view de banco de dados. Porém, relatórios representam leituras derivadas de dados já existentes em `transaction` e `budget`, não estado próprio do domínio.

## Decisão

A feature `report` não possui entidade JPA nem tabela dedicada. Os dados são obtidos consultando os repositórios existentes (`TransactionRepository`, `BudgetRepository`, `AccountRepository`) e as agregações são calculadas na camada de serviço via Java Streams.

## Consequências

**Positivas:**
- Elimina redundância: os dados de transações não são duplicados em uma tabela de relatórios.
- Simplicidade: não há migrations de tabelas/views de relatório para manter sincronizadas com o schema principal.
- Relatórios refletem sempre o estado atual dos dados sem necessidade de processo de sincronização.

**Negativas / trade-offs:**
- Agregações em memória via streams são aceitáveis para o volume pessoal do PFC. Se o volume crescer, a estratégia deve ser revisada — a migração para queries JPQL nativas ou views materializadas exigirá refatoração do `ReportService`.
