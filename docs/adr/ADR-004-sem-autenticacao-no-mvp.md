# ADR-004: Sem autenticação no MVP

**Status:** Accepted  
**Data:** 2026-06-05

## Contexto

Implementar autenticação robusta (Spring Security + JWT, gestão de tokens, refresh, revogação) consome tempo de desenvolvimento e adiciona complexidade que pode desviar o foco do objetivo principal do MVP: validar o modelo de domínio financeiro.

## Decisão

Autenticação e autorização são excluídas do escopo inicial. Nenhuma dependência de Spring Security é adicionada ao projeto nesta fase. A API é acessível sem credenciais.

## Consequências

**Positivas:**
- O desenvolvimento se concentra inteiramente no domínio financeiro (transações, categorias, relatórios).
- A arquitetura package-by-feature permite adicionar um pacote `auth` no futuro sem reescrever as features existentes — a adição de segurança é incremental.
- Menor complexidade nos testes do MVP.

**Negativas / trade-offs:**
- A API não pode ser exposta publicamente; só é segura em ambientes controlados (rede privada, VPN, localhost).
- Em produção, a segurança é responsabilidade da infraestrutura (firewall, proxy reverso).
- Os dados em uso devem ser fictícios até que a camada de autenticação seja implementada (ver `SECURITY.md`).
