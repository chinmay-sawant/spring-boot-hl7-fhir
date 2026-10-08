package com.example.healthcare.support;

import java.time.Duration;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers environment for the end-to-end tests (plan row 6.4 of
 * {@code plans/spring-boot/01-foundation.md}).
 *
 * <p>Two PostgreSQL 17 containers and one HAPI FHIR R4 JPA server run per test
 * JVM on a dedicated user-defined Docker network:
 *
 * <ul>
 *   <li>the application database is wired into the Spring Boot test context
 *       through {@code spring.datasource.*}, so Flyway applies the application
 *       migrations at context startup;</li>
 *   <li>the FHIR database is not reachable from the test JVM at all: only the
 *       HAPI server reaches it, by its network alias {@code fhir-postgres},
 *       exactly like the local compose stack;</li>
 *   <li>the HAPI server must answer {@code 200} on {@code /fhir/metadata}
 *       before any test runs, and the context points its FHIR client at the
 *       mapped host port through {@code healthcare.fhir.base-url}.</li>
 * </ul>
 *
 * <p>The Spring Boot application starts on a random port
 * ({@link SpringBootTest.WebEnvironment#RANDOM_PORT}). The container images
 * are the tags pinned in {@code docker-compose.yml}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    /** Image tags match the local stack in {@code docker-compose.yml}. */
    private static final String POSTGRES_IMAGE = "postgres:17.11";
    private static final String HAPI_FHIR_IMAGE = "hapiproject/hapi:v8.12.0-2-tomcat";

    /** HTTP port of the HAPI FHIR server inside its container. */
    private static final int FHIR_SERVER_PORT = 8080;

    /**
     * The HAPI JPA server creates its schema on first boot and unpacks a large
     * war; on this WSL2 host a cold start takes about 17 minutes, so the poll
     * budget is deliberately generous.
     */
    private static final Duration FHIR_SERVER_STARTUP_TIMEOUT = Duration.ofMinutes(30);

    private static final long CONTAINERS_REQUESTED_AT = System.nanoTime();

    static {
        // The PostgreSQL JDBC driver sends the JVM default time zone in its
        // startup packet, and the postgres:17.11 server rejects legacy aliases
        // such as Asia/Calcutta (this host runs in that zone). Pinning UTC
        // keeps connections independent of host zone configuration, mirroring
        // DatabaseMigrationIT.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    /** Dedicated network: the HAPI server resolves its database by alias. */
    private static final Network NETWORK = Network.newNetwork();

    @Container
    protected static final PostgreSQLContainer<?> APP_DATABASE = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("healthcare")
            .withUsername("healthcare")
            .withPassword("healthcare")
            .withNetwork(NETWORK);

    @Container
    protected static final PostgreSQLContainer<?> FHIR_DATABASE = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("fhir")
            .withUsername("fhir")
            .withPassword("fhir")
            .withNetwork(NETWORK)
            .withNetworkAliases("fhir-postgres");

    @Container
    protected static final GenericContainer<?> FHIR_SERVER = new GenericContainer<>(HAPI_FHIR_IMAGE)
            .withNetwork(NETWORK)
            .dependsOn(FHIR_DATABASE)
            .withExposedPorts(FHIR_SERVER_PORT)
            .withEnv("hapi.fhir.fhir_version", "R4")
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://fhir-postgres:5432/fhir")
            .withEnv("SPRING_DATASOURCE_USERNAME", "fhir")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "fhir")
            .withEnv("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.postgresql.Driver")
            // The image defaults to its H2 dialect; PostgreSQL needs this one,
            // which the image resolves through its own HIBERNATE_DIALECT
            // placeholder.
            .withEnv("HIBERNATE_DIALECT", "ca.uhn.fhir.jpa.model.dialect.HapiFhirPostgresDialect")
            // No Lucene/Elasticsearch index for the learning stack, as in
            // infrastructure/fhir-server/application.yaml.
            .withEnv("SPRING_JPA_PROPERTIES_HIBERNATE_SEARCH_ENABLED", "false")
            .waitingFor(Wait.forHttp("/fhir/metadata")
                    .forPort(FHIR_SERVER_PORT)
                    .forStatusCode(200)
                    .withReadTimeout(Duration.ofSeconds(60))
                    .withStartupTimeout(FHIR_SERVER_STARTUP_TIMEOUT));

    @DynamicPropertySource
    static void registerApplicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", APP_DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", APP_DATABASE::getUsername);
        registry.add("spring.datasource.password", APP_DATABASE::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("healthcare.fhir.base-url", AbstractIntegrationTest::fhirBaseUrl);
    }

    @BeforeAll
    static void reportContainerStartup() {
        LOGGER.info("Integration environment ready after {} ms: app database {}, FHIR database {}, FHIR server {}",
                (System.nanoTime() - CONTAINERS_REQUESTED_AT) / 1_000_000,
                APP_DATABASE.getJdbcUrl(),
                FHIR_DATABASE.getJdbcUrl(),
                fhirBaseUrl());
    }

    /** Base URL of the containerized HAPI FHIR R4 server, as seen by the test JVM. */
    protected static String fhirBaseUrl() {
        return "http://" + FHIR_SERVER.getHost() + ":" + FHIR_SERVER.getMappedPort(FHIR_SERVER_PORT) + "/fhir";
    }
}
