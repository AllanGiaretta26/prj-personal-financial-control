package com.pfc.shared.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Cria e mantém os buckets (algoritmo token-bucket, via Bucket4j) usados pelo
 * {@link RateLimitFilter}, em memória — adequado à escala atual do projeto
 * (instância única; não há necessidade de um backend distribuído como Redis).
 *
 * <p>Os buckets são mantidos em dois {@link ConcurrentHashMap} separados — um
 * por perfil de limite ({@code login} e {@code default}) — para que a mesma
 * chave (ex.: o IP de um usuário não autenticado) não compartilhe o mesmo
 * bucket entre rotas com políticas diferentes. A chave de cada bucket é
 * prefixada por quem a originou (ex.: {@code "ip:" + ip} ou
 * {@code "user:" + email}) para que um IP e um e-mail textualmente iguais
 * jamais colidam.
 *
 * <p><strong>Trade-off</strong>: como o estado vive apenas na JVM local, ele é
 * perdido em um restart e não é compartilhado entre réplicas — se o serviço for
 * escalado horizontalmente, os limites efetivos por chave se tornam
 * "capacidade × nº de instâncias". Aceitável para o estágio atual; uma futura
 * migração para um backend distribuído (ex.: Bucket4j + Redis) substituiria
 * apenas este componente, mantendo o filtro intacto.
 */
@Component
public class RateLimitBucketProvider {

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> defaultBuckets = new ConcurrentHashMap<>();

    private final RateLimitProperties properties;

    public RateLimitBucketProvider(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Bucket do perfil estrito (rotas {@code /api/v1/auth/login} e
     * {@code /api/v1/auth/register}), uma instância por chave — tipicamente
     * {@code "ip:" + ip}, já que essas rotas são públicas e o objetivo é
     * conter força bruta antes da autenticação acontecer.
     */
    public Bucket loginBucket(String key) {
        return loginBuckets.computeIfAbsent(key, buildBucket(properties.getLogin()));
    }

    /**
     * Bucket do perfil generoso (demais rotas {@code /api/v1/**}), por usuário
     * autenticado ({@code "user:" + email}) ou, na ausência de identidade
     * confiável, por IP ({@code "ip:" + ip}).
     */
    public Bucket defaultBucket(String key) {
        return defaultBuckets.computeIfAbsent(key, buildBucket(properties.getDefault()));
    }

    private Function<String, Bucket> buildBucket(RateLimitProperties.Limit limit) {
        return key -> {
            Bandwidth bandwidth = Bandwidth.builder()
                    .capacity(limit.getCapacity())
                    .refillIntervally(limit.getRefillTokens(), Duration.ofSeconds(limit.getRefillPeriodSeconds()))
                    .build();
            return Bucket.builder().addLimit(bandwidth).build();
        };
    }
}
