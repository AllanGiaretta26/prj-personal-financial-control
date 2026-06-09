package com.pfc.shared.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.auth.UserRepository;
import com.pfc.auth.dto.LoginRequest;
import com.pfc.auth.dto.RegisterRequest;
import com.pfc.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que {@link RateLimitFilter} corta requisições em excesso ao endpoint
 * de login com {@code 429} no formato {@link org.springframework.http.ProblemDetail}.
 *
 * <p><strong>Isolamento determinístico</strong>: o filtro mantém os buckets em
 * um {@link RateLimitBucketProvider} singleton — se este teste reaproveitasse o
 * contexto Spring de {@code AuthControllerIT} (que já consome parte da cota do
 * perfil {@code login} a partir do mesmo IP de loopback do MockMvc), o resultado
 * dependeria da ordem de execução dos testes. Em vez de tentar resetar estado
 * compartilhado ou forjar IPs via {@code X-Forwarded-For} (que o filtro
 * intencionalmente não confia — ver Javadoc de {@link RateLimitFilter}), este
 * teste sobrescreve {@code rate-limit.login.*} via {@link TestPropertySource}
 * com uma capacidade bem baixa. Propriedades diferentes geram uma chave de
 * cache de contexto Spring diferente, então o Spring sobe um contexto próprio
 * — com sua própria instância (vazia) de {@link RateLimitBucketProvider} — só
 * para esta classe, tornando o teste determinístico independentemente da ordem
 * ou de execuções anteriores.
 */
@TestPropertySource(properties = {
        "rate-limit.login.capacity=3",
        "rate-limit.login.refill-tokens=3",
        "rate-limit.login.refill-period-seconds=3600"
})
class RateLimitFilterIT extends AbstractIntegrationTest {

    private static final int LOGIN_CAPACITY = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    /**
     * Login e registro compartilham o mesmo bucket "estrito" por IP — ambos
     * são alvo clássico de força bruta (ver Javadoc de {@link RateLimitFilter}).
     * Por isso este teste único soma chamadas às duas rotas até estourar a
     * capacidade configurada ({@value #LOGIN_CAPACITY}): mistura, de propósito,
     * registros e logins, e a {@code (capacity + 1)}-ésima chamada — um login —
     * deve ser cortada com 429. Um único método evita qualquer dependência de
     * ordem de execução entre testes que, de outra forma, disputariam o mesmo
     * bucket dentro do mesmo contexto Spring.
     */
    @Test
    void authEndpoints_beyondConfiguredCapacity_return429ProblemDetailWithRetryAfter() throws Exception {
        String registerBody = objectMapper.writeValueAsString(buildRegisterRequest("flood@example.com", "supersecret1"));
        String loginBody = objectMapper.writeValueAsString(buildLoginRequest("flood@example.com", "wrong-password"));

        // 1ª chamada: registro bem-sucedido (consome 1 token do bucket "ip:127.0.0.1").
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(registerBody))
                .andExpect(status().isCreated());

        // Chamadas restantes até a capacidade: logins com senha errada (401), mas
        // ainda contabilizados — o bucket não distingue sucesso de falha de credenciais.
        for (int i = 1; i < LOGIN_CAPACITY; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content(loginBody))
                    .andExpect(status().isUnauthorized());
        }

        // (capacity + 1)-ésima chamada: bucket esgotado, cortada antes de chegar ao controller.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.detail", not(is(""))));
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private RegisterRequest buildRegisterRequest(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
