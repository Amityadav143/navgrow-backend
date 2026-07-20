/* © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved. */
package com.navgrow.repository;

import com.navgrow.enums.NewsStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for the Postgres native-enum binding bug fixed in July
 * 2026: a JPQL enum LITERAL was rendered as `'PAID'::PaymentStatus` (the Java
 * class name) and failed with "operator does not exist". These queries throw
 * on execution — even against empty tables — if enum binding regresses, so no
 * fixture data is required.
 *
 * Runs against real PostgreSQL via Testcontainers with the full Flyway
 * migration chain (H2 cannot represent the native enums / text[] columns).
 * Requires a local Docker daemon: `mvn test`.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class EnumQueryRegressionIT {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("navgrow_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired OrderRepository orderRepository;
    @Autowired NewsArticleRepository newsRepository;

    @Test
    void sumRevenueFrom_bindsPaymentStatusEnumCorrectly() {
        // Old literal-based query failed here with SQLGrammarException.
        BigDecimal sum = orderRepository.sumRevenueFrom(LocalDateTime.now().minusDays(30));
        assertNotNull(sum);
        assertEquals(0, sum.compareTo(BigDecimal.ZERO));
    }

    @Test
    void newsStatusQueries_bindNativeEnumCorrectly() {
        assertTrue(newsRepository
            .findByStatusOrderByCreatedAtDesc(NewsStatus.PUBLISHED, PageRequest.of(0, 10))
            .isEmpty());
        assertTrue(newsRepository
            .findByStatusOrderByPublishedAtDesc(NewsStatus.PUBLISHED, PageRequest.of(0, 10))
            .isEmpty());
    }

    @Test
    void flywayMigrationChain_appliesCleanlyOnFreshPostgres() {
        // Reaching this point means V1..V11 all applied and Hibernate's
        // schema validation passed against the entities.
        assertTrue(postgres.isRunning());
    }
}
