package com.pfc.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.account.dto.AccountRequest;
import com.pfc.auth.User;
import com.pfc.auth.UserRepository;
import com.pfc.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de account agora exigem autenticação (Spring Security + JWT — ver
 * pacote {@code com.pfc.auth}). Os requests aqui simulam um usuário já
 * autenticado via {@code SecurityMockMvcRequestPostProcessors.user(...)},
 * mantendo o foco do teste no comportamento de account em vez do fluxo de
 * login (coberto por {@code AuthControllerIT}).
 *
 * <p>Como {@code AccountService} agora resolve o usuário atual via
 * {@code AuthenticatedUserProvider} (busca por e-mail no banco), cada e-mail
 * simulado pelo {@code user(...)} precisa corresponder a um {@link User}
 * persistido — daí o helper {@link #persistUser(String)}.
 */
class AccountControllerIT extends AbstractIntegrationTest {

    private static final String AUTHENTICATED_EMAIL = "account-it-user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository repository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUpAuthenticatedUser() {
        persistUser(AUTHENTICATED_EMAIL);
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void crudLifecycle_createReadUpdateDelete_persistsAgainstRealDatabase() throws Exception {
        AccountRequest createRequest = buildRequest("My Checking", AccountType.CHECKING, BigDecimal.TEN);

        String createResponseBody = mockMvc.perform(post("/api/v1/accounts")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("My Checking")))
                .andExpect(jsonPath("$.type", is("CHECKING")))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResponseBody).get("id").asText());

        mockMvc.perform(get("/api/v1/accounts/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.name", is("My Checking")))
                .andExpect(jsonPath("$.initialBalance", is(10.0)));

        AccountRequest updateRequest = buildRequest("New Name", AccountType.SAVINGS, BigDecimal.valueOf(500));

        mockMvc.perform(put("/api/v1/accounts/{id}", id)
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("New Name")))
                .andExpect(jsonPath("$.type", is("SAVINGS")));

        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.findById(id).get().getInitialBalance()).isEqualByComparingTo(BigDecimal.valueOf(500));

        mockMvc.perform(delete("/api/v1/accounts/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/accounts/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNotFound());

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void findById_whenNotFound_returnsNotFoundProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/accounts/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString(id.toString())));
    }

    /**
     * Prova de isolamento entre usuários (autorização por dono): a conta criada
     * pelo usuário A é totalmente invisível para o usuário B — tanto na listagem
     * quanto na busca direta por id — e tentativas de leitura/edição/exclusão por
     * B retornam 404 (nunca 403), para não revelar a outro usuário que o recurso
     * existe sob outra conta. Este é o teste mais importante desta fase: ele
     * comprova que o filtro {@code WHERE owner = :currentUser} está, de fato,
     * sendo aplicado em todas as operações.
     */
    @Test
    void crossUserAccess_userBCannotSeeOrModifyUserAsAccount_returnsNotFound() throws Exception {
        String userAEmail = "account-it-user-a@example.com";
        String userBEmail = "account-it-user-b@example.com";
        persistUser(userAEmail);
        persistUser(userBEmail);

        AccountRequest createRequest = buildRequest("User A Checking", AccountType.CHECKING, BigDecimal.TEN);

        String createResponseBody = mockMvc.perform(post("/api/v1/accounts")
                        .with(user(userAEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID accountId = UUID.fromString(objectMapper.readTree(createResponseBody).get("id").asText());

        // User B cannot read user A's account — 404, not 403 (no existence leak)
        mockMvc.perform(get("/api/v1/accounts/{id}", accountId).with(user(userBEmail)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        // User B's listing must not include user A's account
        mockMvc.perform(get("/api/v1/accounts").with(user(userBEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));

        // User B cannot update user A's account — 404
        AccountRequest updateRequest = buildRequest("Hijacked", AccountType.SAVINGS, BigDecimal.ONE);
        mockMvc.perform(put("/api/v1/accounts/{id}", accountId)
                        .with(user(userBEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        // User B cannot delete user A's account — 404
        mockMvc.perform(delete("/api/v1/accounts/{id}", accountId).with(user(userBEmail)))
                .andExpect(status().isNotFound());

        // The account survives untouched and user A still sees it
        assertThat(repository.findById(accountId)).isPresent();
        assertThat(repository.findById(accountId).get().getName()).isEqualTo("User A Checking");

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId).with(user(userAEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("User A Checking")));
    }

    private AccountRequest buildRequest(String name, AccountType type, BigDecimal initialBalance) {
        AccountRequest request = new AccountRequest();
        request.setName(name);
        request.setType(type);
        request.setInitialBalance(initialBalance);
        return request;
    }

    /**
     * Persiste um {@link User} mínimo com o e-mail informado, para que
     * {@code AuthenticatedUserProvider} consiga resolver o "usuário atual"
     * a partir do principal simulado por {@code SecurityMockMvcRequestPostProcessors.user(...)}.
     * O hash de senha é um valor fixo qualquer — login não é exercitado aqui.
     */
    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01");
        return userRepository.save(user);
    }
}
