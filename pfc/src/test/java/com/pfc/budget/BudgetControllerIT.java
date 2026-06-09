package com.pfc.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.auth.User;
import com.pfc.auth.UserRepository;
import com.pfc.budget.dto.BudgetRequest;
import com.pfc.category.Category;
import com.pfc.category.CategoryRepository;
import com.pfc.category.CategoryType;
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
 * Endpoints de budget exigem autenticação (Spring Security + JWT — ver pacote
 * {@code com.pfc.auth}). Os requests aqui simulam um usuário já autenticado via
 * {@code SecurityMockMvcRequestPostProcessors.user(...)} — como
 * {@code BudgetService} resolve o usuário atual via {@code AuthenticatedUserProvider}
 * (busca por e-mail no banco), cada e-mail simulado precisa corresponder a um
 * {@link User} persistido (ver {@link #persistUser(String)}).
 */
class BudgetControllerIT extends AbstractIntegrationTest {

    private static final String AUTHENTICATED_EMAIL = "budget-it-user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BudgetRepository budgetRepository;

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
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void crudLifecycle_createReadUpdateDelete_persistsAgainstRealDatabase() throws Exception {
        Category category = persistCategory(authenticatedUser, "Food", CategoryType.EXPENSE);

        BudgetRequest createRequest = buildRequest(category.getId(), "2025-01", new BigDecimal("500.00"));

        String createResponseBody = mockMvc.perform(post("/api/v1/budgets")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId", is(category.getId().toString())))
                .andExpect(jsonPath("$.referenceMonth", is("2025-01")))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResponseBody).get("id").asText());

        mockMvc.perform(get("/api/v1/budgets/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.limitAmount", is(500.0)));

        BudgetRequest updateRequest = buildRequest(category.getId(), "2025-01", new BigDecimal("750.00"));

        mockMvc.perform(put("/api/v1/budgets/{id}", id)
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitAmount", is(750.0)));

        assertThat(budgetRepository.findById(id)).isPresent();
        assertThat(budgetRepository.findById(id).get().getLimitAmount()).isEqualByComparingTo(new BigDecimal("750.00"));

        mockMvc.perform(delete("/api/v1/budgets/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/budgets/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNotFound());

        assertThat(budgetRepository.findById(id)).isEmpty();
    }

    @Test
    void findById_whenNotFound_returnsNotFoundProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/budgets/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString(id.toString())));
    }

    /**
     * Prova de isolamento entre usuários: o orçamento criado pelo usuário A é
     * invisível para o usuário B, e tentativas de leitura/edição/exclusão por B
     * retornam 404 (nunca 403).
     */
    @Test
    void crossUserAccess_userBCannotSeeOrModifyUserAsBudget_returnsNotFound() throws Exception {
        String userAEmail = "budget-it-user-a@example.com";
        String userBEmail = "budget-it-user-b@example.com";
        User userA = persistUser(userAEmail);
        persistUser(userBEmail);

        Category categoryA = persistCategory(userA, "User A Category", CategoryType.EXPENSE);

        BudgetRequest createRequest = buildRequest(categoryA.getId(), "2025-03", new BigDecimal("400.00"));

        String createResponseBody = mockMvc.perform(post("/api/v1/budgets")
                        .with(user(userAEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID budgetId = UUID.fromString(objectMapper.readTree(createResponseBody).get("id").asText());

        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId).with(user(userBEmail)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        mockMvc.perform(get("/api/v1/budgets").with(user(userBEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));

        BudgetRequest updateRequest = buildRequest(categoryA.getId(), "2025-03", new BigDecimal("999.00"));
        mockMvc.perform(put("/api/v1/budgets/{id}", budgetId)
                        .with(user(userBEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/budgets/{id}", budgetId).with(user(userBEmail)))
                .andExpect(status().isNotFound());

        assertThat(budgetRepository.findById(budgetId)).isPresent();
    }

    /**
     * Prova de que a validação cruzada de posse (IDOR) funciona para budget: o
     * usuário B não consegue criar um orçamento apontando para a categoria do
     * usuário A — o lookup é restrito ao próprio dono ({@code findByIdAndOwner}),
     * então a referência "não existe" do ponto de vista de B, resultando em 404.
     */
    @Test
    void create_whenReferencingAnotherUsersCategory_returnsNotFound() throws Exception {
        String userAEmail = "budget-it-owner-a@example.com";
        String userBEmail = "budget-it-owner-b@example.com";
        User userA = persistUser(userAEmail);
        persistUser(userBEmail);

        Category categoryA = persistCategory(userA, "User A Category", CategoryType.EXPENSE);

        BudgetRequest crossRequest = buildRequest(categoryA.getId(), "2025-04", new BigDecimal("200.00"));

        mockMvc.perform(post("/api/v1/budgets")
                        .with(user(userBEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(crossRequest)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString(categoryA.getId().toString())));

        assertThat(budgetRepository.findAll()).isEmpty();
    }

    private BudgetRequest buildRequest(UUID categoryId, String referenceMonth, BigDecimal limitAmount) {
        BudgetRequest request = new BudgetRequest();
        request.setCategoryId(categoryId);
        request.setReferenceMonth(referenceMonth);
        request.setLimitAmount(limitAmount);
        return request;
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01");
        return userRepository.save(user);
    }

    private Category persistCategory(User owner, String name, CategoryType type) {
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setOwner(owner);
        return categoryRepository.save(category);
    }
}
