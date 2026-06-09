package com.pfc.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.auth.dto.LoginRequest;
import com.pfc.auth.dto.RegisterRequest;
import com.pfc.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractIntegrationTest {

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

    @Test
    void register_withNewEmail_returns201WithToken() throws Exception {
        RegisterRequest request = buildRegisterRequest("alice@example.com", "supersecret1");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", not(is(""))))
                .andExpect(jsonPath("$.expiresAt").exists());

        assertThatUserWasPersistedWithHashedPassword("alice@example.com", "supersecret1");
    }

    @Test
    void register_withDuplicateEmail_returnsUnprocessableEntityProblemDetail() throws Exception {
        RegisterRequest request = buildRegisterRequest("bob@example.com", "supersecret1");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("bob@example.com")));
    }

    @Test
    void login_withCorrectCredentials_returns200WithToken() throws Exception {
        registerUser("carol@example.com", "supersecret1");

        LoginRequest loginRequest = buildLoginRequest("carol@example.com", "supersecret1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(is(""))))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void login_withWrongPassword_returns401ProblemDetail() throws Exception {
        registerUser("dave@example.com", "supersecret1");

        LoginRequest loginRequest = buildLoginRequest("dave@example.com", "totally-wrong-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void login_withUnknownEmail_returns401ProblemDetail() throws Exception {
        LoginRequest loginRequest = buildLoginRequest("ghost@example.com", "whatever-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void protectedEndpoint_withoutToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        String token = registerUser("erin@example.com", "supersecret1");

        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer this-is-not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void responseHeaders_includeSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    private String registerUser(String email, String password) throws Exception {
        RegisterRequest request = buildRegisterRequest(email, password);

        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(responseBody).get("token").asText();
    }

    private void assertThatUserWasPersistedWithHashedPassword(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getPasswordHash()).isNotEqualTo(rawPassword);
        org.assertj.core.api.Assertions.assertThat(user.getPasswordHash()).startsWith("$2");
    }

    private RegisterRequest buildRegisterRequest(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
