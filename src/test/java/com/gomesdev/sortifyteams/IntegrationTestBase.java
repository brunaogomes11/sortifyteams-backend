package com.gomesdev.sortifyteams;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração: sobe um PostgreSQL efêmero (Testcontainers),
 * independente do .env (plan.md, D7).
 *
 * Padrão singleton: o container é iniciado uma única vez por JVM e
 * compartilhado por todas as classes (o contexto Spring é cacheado entre
 * classes — usar @Container derrubaria o banco no fim da primeira classe e
 * quebraria as seguintes). O Ryuk do Testcontainers encerra o container ao
 * fim da JVM.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=c2VncmVkby1kZS10ZXN0ZS1jb20tdGFtYW5oby1zdWZpY2llbnRlLXBhcmEtaHMyNTY=",
        "app.geocoding.enabled=false"
})
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
