package com.pfc.shared;

import com.pfc.auth.User;
import com.pfc.auth.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que erros de cliente sobre rotas protegidas voltem com o status HTTP
 * correto, em {@code ProblemDetail}, e <strong>sem vazar stacktrace</strong> —
 * em vez de serem mascarados como 401 pelo encaminhamento ao endpoint
 * {@code /error} (que é autenticado). Ver {@code GlobalExceptionHandler} e
 * SECURITY.md &gt; "Validação de segurança".
 */
class ErrorHandlingIT extends AbstractIntegrationTest {

    private static final String AUTHENTICATED_EMAIL = "error-it-user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail(AUTHENTICATED_EMAIL);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01");
        userRepository.save(user);
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void malformedJsonBody_returnsBadRequest_notMaskedAs401() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content("{ not valid json "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void nonUuidPathVariable_returnsBadRequest_notMaskedAs401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", "nao-e-uuid").with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void errorResponse_doesNotLeakStacktraceOrInternalDetails() throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content("{ not valid json "))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("stacktrace")
                .doesNotContain("at com.pfc");
    }
}
