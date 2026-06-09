package com.pfc.shared.ratelimit;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o consumo de tokens isoladamente — sem subir contexto Spring nem
 * passar pelo filtro/servlet — para validar a lógica central do rate limiting:
 * capacidade, esgotamento, e isolamento entre chaves e perfis distintos.
 */
class RateLimitBucketProviderTest {

    @Test
    void loginBucket_allowsConsumptionUpToConfiguredCapacity() {
        RateLimitBucketProvider provider = providerWithLimits(/* loginCapacity */ 3, /* defaultCapacity */ 100);
        Bucket bucket = provider.loginBucket("ip:127.0.0.1");

        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isTrue();
    }

    @Test
    void loginBucket_rejectsConsumptionBeyondConfiguredCapacity() {
        RateLimitBucketProvider provider = providerWithLimits(3, 100);
        Bucket bucket = provider.loginBucket("ip:127.0.0.1");

        assertThat(bucket.tryConsumeAsMuchAsPossible()).isEqualTo(3);
        assertThat(bucket.tryConsume(1)).isFalse();
    }

    @Test
    void loginBucket_isolatesDistinctKeysFromEachOther() {
        RateLimitBucketProvider provider = providerWithLimits(1, 100);

        Bucket first = provider.loginBucket("ip:10.0.0.1");
        Bucket second = provider.loginBucket("ip:10.0.0.2");

        assertThat(first.tryConsume(1)).isTrue();
        assertThat(first.tryConsume(1)).isFalse();
        // Chave diferente: bucket independente, ainda com seu token inicial intacto.
        assertThat(second.tryConsume(1)).isTrue();
    }

    @Test
    void loginBucket_returnsSameBucketInstanceForSameKey() {
        RateLimitBucketProvider provider = providerWithLimits(5, 100);

        Bucket first = provider.loginBucket("ip:127.0.0.1");
        Bucket second = provider.loginBucket("ip:127.0.0.1");

        assertThat(first).isSameAs(second);
    }

    @Test
    void loginAndDefaultBuckets_forTheSameKeyText_areIndependent() {
        RateLimitBucketProvider provider = providerWithLimits(/* loginCapacity */ 1, /* defaultCapacity */ 5);

        Bucket loginBucket = provider.loginBucket("ip:127.0.0.1");
        Bucket defaultBucket = provider.defaultBucket("ip:127.0.0.1");

        assertThat(loginBucket).isNotSameAs(defaultBucket);
        // Esgota o bucket "estrito"...
        assertThat(loginBucket.tryConsume(1)).isTrue();
        assertThat(loginBucket.tryConsume(1)).isFalse();
        // ...sem afetar o bucket "generoso" para a mesma chave textual.
        assertThat(defaultBucket.tryConsume(1)).isTrue();
    }

    @Test
    void defaultBucket_usesItsOwnConfiguredCapacity() {
        RateLimitBucketProvider provider = providerWithLimits(1, 60);
        Bucket bucket = provider.defaultBucket("user:alice@example.com");

        assertThat(bucket.tryConsumeAsMuchAsPossible()).isEqualTo(60);
        assertThat(bucket.tryConsume(1)).isFalse();
    }

    private RateLimitBucketProvider providerWithLimits(long loginCapacity, long defaultCapacity) {
        RateLimitProperties properties = new RateLimitProperties();

        RateLimitProperties.Limit login = new RateLimitProperties.Limit();
        login.setCapacity(loginCapacity);
        login.setRefillTokens(loginCapacity);
        login.setRefillPeriodSeconds(3600);
        properties.setLogin(login);

        RateLimitProperties.Limit defaultLimit = new RateLimitProperties.Limit();
        defaultLimit.setCapacity(defaultCapacity);
        defaultLimit.setRefillTokens(defaultCapacity);
        defaultLimit.setRefillPeriodSeconds(3600);
        properties.setDefault(defaultLimit);

        return new RateLimitBucketProvider(properties);
    }
}
