package com.gomesdev.sortifyteams;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base dos testes de integração: sobe um PostgreSQL efêmero (Testcontainers),
 * independente do .env (plan.md, D7). O container é compartilhado entre as
 * classes que estendem esta base (singleton por JVM).
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "jwt.secret=c2VncmVkby1kZS10ZXN0ZS1jb20tdGFtYW5oby1zdWZpY2llbnRlLXBhcmEtaHMyNTY=")
public abstract class IntegrationTestBase {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");
}
