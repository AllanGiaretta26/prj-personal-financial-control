package com.pfc.shared.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * Vincula as propriedades {@code rate-limit.*} (ver {@code application.yaml}).
 *
 * <p>Dois perfis de limite são suportados: {@code login} (estrito, aplicado por
 * IP às rotas {@code /api/v1/auth/login} e {@code /api/v1/auth/register} — alvo
 * clássico de força bruta) e {@code default} (mais generoso, aplicado por
 * usuário autenticado — ou por IP, na ausência de token válido — às demais
 * rotas {@code /api/v1/**}). Cada perfil define um {@link Bandwidth} no estilo
 * "capacidade + recarga": {@code capacity} tokens iniciais, recarregando
 * {@code refillTokens} a cada {@code refillPeriodSeconds}.
 */
@Component
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    @NestedConfigurationProperty
    private Limit login = new Limit();

    @NestedConfigurationProperty
    private Limit defaultLimit = new Limit();

    public Limit getDefault() {
        return defaultLimit;
    }

    public void setDefault(Limit defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    @Getter
    @Setter
    public static class Limit {
        private long capacity;
        private long refillTokens;
        private long refillPeriodSeconds;
    }
}
