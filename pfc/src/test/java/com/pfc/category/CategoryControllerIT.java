package com.pfc.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.auth.User;
import com.pfc.auth.UserRepository;
import com.pfc.category.dto.CategoryRequest;
import com.pfc.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

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
 * Endpoints de category exigem autenticação (Spring Security + JWT — ver pacote
 * {@code com.pfc.auth}). Os requests aqui simulam um usuário já autenticado via
 * {@code SecurityMockMvcRequestPostProcessors.user(...)} — como
 * {@code CategoryService} resolve o usuário atual via {@code AuthenticatedUserProvider}
 * (busca por e-mail no banco), cada e-mail simulado precisa corresponder a um
 * {@link User} persistido (ver {@link #persistUser(String)}).
 */
class CategoryControllerIT extends AbstractIntegrationTest {

    private static final String AUTHENTICATED_EMAIL = "category-it-user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository repository;

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
        CategoryRequest createRequest = buildRequest("Salary", CategoryType.INCOME);

        String createResponseBody = mockMvc.perform(post("/api/v1/categories")
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Salary")))
                .andExpect(jsonPath("$.type", is("INCOME")))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResponseBody).get("id").asText());

        mockMvc.perform(get("/api/v1/categories/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.name", is("Salary")));

        CategoryRequest updateRequest = buildRequest("Bonus", CategoryType.INCOME);

        mockMvc.perform(put("/api/v1/categories/{id}", id)
                        .with(user(AUTHENTICATED_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Bonus")));

        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.findById(id).get().getName()).isEqualTo("Bonus");

        mockMvc.perform(delete("/api/v1/categories/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/categories/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNotFound());

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void findById_whenNotFound_returnsNotFoundProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/categories/{id}", id).with(user(AUTHENTICATED_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString(id.toString())));
    }

    /**
     * Prova de isolamento entre usuários: a categoria criada pelo usuário A é
     * invisível para o usuário B (sem lista compartilhada/global de categorias —
     * cada usuário tem seu próprio conjunto privado), e tentativas de
     * leitura/edição/exclusão por B retornam 404 (nunca 403).
     */
    @Test
    void crossUserAccess_userBCannotSeeOrModifyUserAsCategory_returnsNotFound() throws Exception {
        String userAEmail = "category-it-user-a@example.com";
        String userBEmail = "category-it-user-b@example.com";
        persistUser(userAEmail);
        persistUser(userBEmail);

        CategoryRequest createRequest = buildRequest("User A Groceries", CategoryType.EXPENSE);

        String createResponseBody = mockMvc.perform(post("/api/v1/categories")
                        .with(user(userAEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID categoryId = UUID.fromString(objectMapper.readTree(createResponseBody).get("id").asText());

        mockMvc.perform(get("/api/v1/categories/{id}", categoryId).with(user(userBEmail)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        mockMvc.perform(get("/api/v1/categories").with(user(userBEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));

        CategoryRequest updateRequest = buildRequest("Hijacked", CategoryType.INCOME);
        mockMvc.perform(put("/api/v1/categories/{id}", categoryId)
                        .with(user(userBEmail))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId).with(user(userBEmail)))
                .andExpect(status().isNotFound());

        assertThat(repository.findById(categoryId)).isPresent();
        assertThat(repository.findById(categoryId).get().getName()).isEqualTo("User A Groceries");
    }

    private CategoryRequest buildRequest(String name, CategoryType type) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        request.setType(type);
        return request;
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01");
        return userRepository.save(user);
    }
}
