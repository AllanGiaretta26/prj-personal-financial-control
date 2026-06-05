# ADR-001: Organização por feature (package-by-feature)

**Status:** Accepted  
**Data:** 2026-06-05

## Contexto

Ao estruturar o projeto era necessário definir como organizar os pacotes Java. A abordagem convencional em tutoriais Spring Boot é agrupar por camada técnica (`controllers/`, `services/`, `repositories/`), o que coloca artefatos de features diferentes no mesmo pacote.

## Decisão

Adotar package-by-feature: cada domínio do negócio (ex.: `transaction`, `category`, `report`) possui seu próprio pacote contendo todos os artefatos necessários — controller, service, repository, DTOs e entidades.

## Consequências

**Positivas:**
- Cada feature é autocontida; é possível localizar, entender e modificar tudo relacionado a um domínio sem navegar por múltiplos pacotes.
- Mudanças ficam localizadas: alterar a feature `transaction` não exige tocar em pacotes de outras features.
- Facilita desenvolvimento paralelo por feature sem conflitos de merge em arquivos compartilhados.
- Coesão alta e acoplamento baixo entre features é visível na própria estrutura de diretórios.

**Negativas / trade-offs:**
- Menos familiar para desenvolvedores vindos de tutoriais Spring Boot convencionais, exigindo uma breve curva de adaptação.
- Não há separação visual imediata de "todas as entidades" ou "todos os repositórios" do projeto.
