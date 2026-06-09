package com.pfc.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.account.Account;
import com.pfc.account.AccountRepository;
import com.pfc.account.AccountType;
import com.pfc.auth.User;
import com.pfc.auth.UserRepository;
import com.pfc.category.Category;
import com.pfc.category.CategoryRepository;
import com.pfc.category.CategoryType;
import com.pfc.shared.AbstractIntegrationTest;
import com.pfc.transaction.dto.TransactionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Endpoints de transaction exigem autenticação (Spring Security + JWT — ver pacote
 * {@code com.pfc.auth}). Os requests aqui simulam um usuário já autenticado via
 * {@code SecurityMockMvcRequestPostProcessors.user(...)} — como
 * {@code TransactionService} resolve o usuário atual via {@code AuthenticatedUserProvider}
 * (busca por e-mail no banco), cada e-mail simulado precisa corresponder a um
 * {@link User} persistido (ver {@link #persistUser(String)}).
 */
class TransactionControllerIT extends AbstractIntegrationTest {

    private static final String AUTHENTICATED_EMAIL = "transaction-it-user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private User authenticatedUser;

    @BeforeEach
    void setUpAuthenticatedUser() {
        authenticatedUser = persistUser(AUTHENTICATED_EMAIL);
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void crudLifecycle_createReadUpdateDelete_persistsAgainstRealDatabase() throws Exception {
        Account account = persistAccount(authenticatedUser, "Main Checking");
        Category category = persistCategory(authenticatedUser, "Food", CategoryType.EXPENSE);

        TransactionRequest createRequest = buildRequest("Groceries", new BigDecimal("150.00"),
                TransactionType.EXPENSE, account.getId(), category.getId());

        String createResponseBody = mockMvc.perform(post("/api/v1/transactions")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description", is("Groceries")))
                .andExpect(jsonPath("$.accountId", is(account.getId().toString())))
                .andExpect(jsonPath("$.categoryId", is(category.getId().toString())))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResponseBody).get("id").asText());

        mockMvc.perform(get("/api/v1/transactions/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.description", is("Groceries")));

        TransactionRequest updateRequest = buildRequest("Groceries (updated)", new BigDecimal("175.00"),
                TransactionType.EXPENSE, account.getId(), category.getId());

        mockMvc.perform(put("/api/v1/transactions/{id}", id)
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Groceries (updated)")));

        assertThat(transactionRepository.findById(id)).isPresent();
        assertThat(transactionRepository.findById(id).get().getAmount()).isEqualByComparingTo(new BigDecimal("175.00"));

        mockMvc.perform(delete("/api/v1/transactions/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.findById(id)).isEmpty();
    }

    @Test
    void findById_whenNotFound_returnsNotFoundProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/transactions/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString(id.toString())));
    }

    /**
     * Prova de isolamento entre usuários: a transação criada pelo usuário A é
     * invisível para o usuário B, e tentativas de leitura/edição/exclusão por B
     * retornam 404 (nunca 403).
     */
    @Test
    void crossUserAccess_userBCannotSeeOrModifyUserAsTransaction_returnsNotFound() throws Exception {
        String userAEmail = "transaction-it-user-a@example.com";
        String userBEmail = "transaction-it-user-b@example.com";
        User userA = persistUser(userAEmail);
        persistUser(userBEmail);

        Account accountA = persistAccount(userA, "User A Account");
        Category categoryA = persistCategory(userA, "User A Category", CategoryType.EXPENSE);

        TransactionRequest createRequest = buildRequest("User A Purchase", new BigDecimal("99.90"),
                TransactionType.EXPENSE, accountA.getId(), categoryA.getId());

        String createResponseBody = mockMvc.perform(post("/api/v1/transactions")
                        .with(user(userAEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID transactionId = UUID.fromString(objectMapper.readTree(createResponseBody).get("id").asText());

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId).with(user(userBEmail)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        mockMvc.perform(get("/api/v1/transactions").with(user(userBEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));

        TransactionRequest updateRequest = buildRequest("Hijacked", BigDecimal.ONE,
                TransactionType.EXPENSE, accountA.getId(), categoryA.getId());
        mockMvc.perform(put("/api/v1/transactions/{id}", transactionId)
                        .with(user(userBEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/transactions/{id}", transactionId).with(user(userBEmail)))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.findById(transactionId)).isPresent();
    }

    /**
     * Prova de que a validação cruzada de posse (IDOR) funciona: o usuário B não
     * consegue criar uma transação apontando para a conta/categoria do usuário A —
     * o lookup é restrito ao próprio dono ({@code findByIdAndOwner}), então a
     * referência "não existe" do ponto de vista de B, resultando em 404.
     */
    @Test
    void create_whenReferencingAnotherUsersAccountOrCategory_returnsNotFound() throws Exception {
        String userAEmail = "transaction-it-owner-a@example.com";
        String userBEmail = "transaction-it-owner-b@example.com";
        User userA = persistUser(userAEmail);
        User userB = persistUser(userBEmail);

        Account accountA = persistAccount(userA, "User A Account");
        Category categoryA = persistCategory(userA, "User A Category", CategoryType.EXPENSE);
        Account accountB = persistAccount(userB, "User B Account");
        Category categoryB = persistCategory(userB, "User B Category", CategoryType.EXPENSE);

        // User B tries to create a transaction pointing at user A's account (but own category)
        TransactionRequest crossAccountRequest = buildRequest("Cross-account attempt", BigDecimal.TEN,
                TransactionType.EXPENSE, accountA.getId(), categoryB.getId());

        mockMvc.perform(post("/api/v1/transactions")
                        .with(user(userBEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(crossAccountRequest)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString(accountA.getId().toString())));

        // User B tries to create a transaction pointing at user A's category (but own account)
        TransactionRequest crossCategoryRequest = buildRequest("Cross-category attempt", BigDecimal.TEN,
                TransactionType.EXPENSE, accountB.getId(), categoryA.getId());

        mockMvc.perform(post("/api/v1/transactions")
                        .with(user(userBEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(crossCategoryRequest)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString(categoryA.getId().toString())));

        assertThat(transactionRepository.findAll()).isEmpty();
    }

    private TransactionRequest buildRequest(String description, BigDecimal amount, TransactionType type,
                                             UUID accountId, UUID categoryId) {
        TransactionRequest request = new TransactionRequest();
        request.setDescription(description);
        request.setAmount(amount);
        request.setOccurredOn(LocalDate.now());
        request.setType(type);
        request.setAccountId(accountId);
        request.setCategoryId(categoryId);
        return request;
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01");
        return userRepository.save(user);
    }

    private Account persistAccount(User owner, String name) {
        Account account = new Account();
        account.setName(name);
        account.setType(AccountType.CHECKING);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setOwner(owner);
        return accountRepository.save(account);
    }

    private Category persistCategory(User owner, String name, CategoryType type) {
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setOwner(owner);
        return categoryRepository.save(category);
    }
}
