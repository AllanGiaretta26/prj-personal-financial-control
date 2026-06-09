package com.pfc.shared;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Configuration(proxyBeanMethods = false)
    static class ContainerConfiguration {

        // @ServiceConnection wires datasource url/username/password automatically,
        // so no manual @DynamicPropertySource is needed and Flyway runs against
        // this container on context startup using the real Postgres dialect.
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>("postgres:16");
        }
    }
}
